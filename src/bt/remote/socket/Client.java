package bt.remote.socket;

import java.io.EOFException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import bt.async.Async;
import bt.async.AsyncException;
import bt.async.AsyncManager;
import bt.async.Data;
import bt.remote.socket.data.Acknowledge;
import bt.remote.socket.data.DataProcessor;
import bt.remote.socket.data.KeepAlive;
import bt.remote.socket.data.Request;
import bt.remote.socket.data.Response;
import bt.remote.socket.evnt.ConnectionLost;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;
import bt.utils.StringID;

/**
 * @author &#8904
 *
 */
public class Client implements Killable, Runnable
{
    protected Socket socket;
    protected ObjectInputStream in;
    protected ObjectOutputStream out;
    protected DataProcessor dataProcessor;
    protected boolean running;
    protected String host;
    protected int port;
    protected long currentPing;
    protected long keepAliveTimeout = 10000;
    protected Dispatcher eventDispatcher;

    public Client(Socket socket) throws IOException
    {
        this.eventDispatcher = new Dispatcher();
        this.socket = socket;
        this.host = this.socket.getInetAddress().getHostAddress();
        this.port = this.socket.getPort();
        this.out = new ObjectOutputStream(this.socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(this.socket.getInputStream());
        InstanceKiller.killOnShutdown(this);
    }

    public Client(String host, int port) throws IOException
    {
        this(new Socket(host, port));
    }

    public Dispatcher getEventDispatcher()
    {
        return this.eventDispatcher;
    }

    public void setRequestProcessor(DataProcessor dataProcessor)
    {
        this.dataProcessor = dataProcessor;
    }

    protected void handleIncomingRequest(Request request) throws IOException
    {
        Data response = null;

        if (this.dataProcessor != null)
        {
            Object ret = this.dataProcessor.process(request.getData());

            if (ret != null)
            {
                response = new Data(ret.getClass(), ret, request.getData().getID());
            }
        }

        if (response != null)
        {
            sendResponse(response);
        }
        else
        {
            sendObject(new Acknowledge(new Data(Object.class, null, request.getData().getID())));
        }
    }

    protected void handleIncomingResponse(Response response)
    {
        AsyncManager.get().addData(response.getData());
    }

    protected void handleIncomingAcknowledge(Acknowledge ack)
    {
        AsyncManager.get().addData(ack.getData());
    }

    protected void handleIncomingKeepAlive(KeepAlive ka)
    {
        try
        {
            sendObject(new Acknowledge(new Data(String.class, "Pong", ka.getData().getID())));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public <T> Async<T> send(Object data) throws IOException
    {
        Data<T> outgoingData = new Data(data.getClass(), data, StringID.uniqueID());
        return send(outgoingData);
    }

    public <T> Async<T> send(Data data) throws IOException
    {
        Async async = new Async(data.getID());
        sendObject(new Request(data));
        return async;
    }

    protected void sendResponse(Data data) throws IOException
    {
        sendObject(new Response(data));
    }

    protected synchronized void sendObject(Object obj) throws IOException
    {
        try
        {
            this.out.writeObject(obj);
        }
        catch (NotSerializableException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        System.out.println("Killing client " + this.host + ":" + this.port);
        this.running = false;
        Exceptions.ignoreThrow(() -> Null.checkClose(this.in));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.out));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.socket));

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    public void start()
    {
        this.running = true;
        Threads.get().execute(this, "Client " + this.host + ":" + this.port);
        Threads.get().execute(this::sendKeepAlive, "Ping-Thread " + this.host + ":" + this.port);
    }

    protected void sendKeepAlive()
    {
        while (this.running && !this.socket.isClosed())
        {
            Exceptions.ignoreThrow(() -> Thread.sleep(this.keepAliveTimeout));

            try
            {
                Data data = new Data(String.class, "Ping", StringID.uniqueID());
                Async async = new Async(data.getID());
                long sent = System.currentTimeMillis();
                sendObject(new KeepAlive(data));
                async.get(this.keepAliveTimeout);
                this.currentPing = System.currentTimeMillis() - sent;
            }
            catch (AsyncException e)
            {
                System.err.println("KeepAlive acknowledge took longer than " + this.keepAliveTimeout + " ms. Shutting client down.");
                this.eventDispatcher.dispatch(new ConnectionLost(this));
                kill();
            }
            catch (SocketException sok)
            {
                System.err.println("Connection broken. Client " + this.host + ":" + this.port);
                this.eventDispatcher.dispatch(new ConnectionLost(this));
                kill();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        while (this.running && !this.socket.isClosed())
        {
            try
            {
                Object incoming = this.in.readObject();

                Threads.get().executeCached(() ->
                {
                    try
                    {
                        if (incoming instanceof Request)
                        {
                            handleIncomingRequest((Request)incoming);
                        }
                        else if (incoming instanceof Response)
                        {
                            handleIncomingResponse((Response)incoming);
                        }
                        else if (incoming instanceof KeepAlive)
                        {
                            handleIncomingKeepAlive((KeepAlive)incoming);
                        }
                        else if (incoming instanceof Acknowledge)
                        {
                            handleIncomingAcknowledge((Acknowledge)incoming);
                        }
                    }
                    catch (IOException e)
                    {
                        // ignore
                    }
                });
            }
            catch (EOFException eof)
            {
                System.err.println("Connection lost. Client " + this.host + ":" + this.port);
                this.eventDispatcher.dispatch(new ConnectionLost(this));
                kill();
            }
            catch (IOException io)
            {
                // ignore
            }
            catch (ClassNotFoundException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * @return the host
     */
    public String getHost()
    {
        return this.host;
    }

    /**
     * @param host
     *            the host to set
     */
    public void setHost(String host)
    {
        this.host = host;
    }

    /**
     * @return the port
     */
    public int getPort()
    {
        return this.port;
    }

    /**
     * @param port
     *            the port to set
     */
    public void setPort(int port)
    {
        this.port = port;
    }

    /**
     * @return the keepAliveTimeout
     */
    public long getKeepAliveTimeout()
    {
        return this.keepAliveTimeout;
    }

    /**
     * @param keepAliveTimeout
     *            the keepAliveTimeout to set
     */
    public void setKeepAliveTimeout(long keepAliveTimeout)
    {
        this.keepAliveTimeout = keepAliveTimeout;
    }

    /**
     * @return the currentPing
     */
    public long getCurrentPing()
    {
        return this.currentPing;
    }
}