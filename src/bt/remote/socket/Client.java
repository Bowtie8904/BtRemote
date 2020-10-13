package bt.remote.socket;

import java.io.EOFException;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
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
import bt.remote.socket.evnt.ReconnectFailed;
import bt.remote.socket.evnt.ReconnectStarted;
import bt.remote.socket.evnt.ReconnectSuccessfull;
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
    protected volatile boolean running;
    protected String host;
    protected int port;
    protected long currentPing;
    protected long keepAliveTimeout = 10000;
    protected Dispatcher eventDispatcher;
    protected boolean autoReconnect;
    protected int maxReconnectAttempts = -1;

    public Client(Socket socket) throws IOException
    {
        this.eventDispatcher = new Dispatcher();
        setupConnection(socket);
        InstanceKiller.killOnShutdown(this);
    }

    public Client(String host, int port) throws IOException
    {
        this(new Socket(host, port));
    }

    protected void setupConnection(Socket socket) throws IOException
    {
        this.socket = socket;
        this.host = this.socket.getInetAddress().getHostAddress();
        this.port = this.socket.getPort();
        this.out = new ObjectOutputStream(this.socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(this.socket.getInputStream());
    }

    /**
     * Instructs the client to automatically reconnect to the server if the connection breaks.
     *
     * @param maxReconnectAttempts
     *            The maximum attempts before giving up reconnecting. Set to -1 for infinite attempts.
     */
    public void autoReconnect(int maxReconnectAttempts)
    {
        this.autoReconnect = true;
        this.maxReconnectAttempts = maxReconnectAttempts;
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

        closeResources();

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    protected void closeResources()
    {
        this.running = false;
        Exceptions.ignoreThrow(() -> Null.checkClose(this.in));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.out));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.socket));
    }

    protected void reconnect()
    {
        boolean reconnected = false;
        this.eventDispatcher.dispatch(new ReconnectStarted(this));

        for (int i = 1; i <= this.maxReconnectAttempts || this.maxReconnectAttempts == -1; i ++ )
        {
            System.out.println("Client " + this.host + ":" + this.port + " attempting to reconnect (attempt: " + i + ")");

            try
            {
                Socket newSocket = new Socket(this.host, this.port);
                setupConnection(newSocket);
                reconnected = true;
                start();
                break;
            }
            catch (ConnectException e)
            {
                System.err.println("Client " + this.host + ":" + this.port + " reconnect attempt " + i + " failed.");
            }
            catch (IOException e1)
            {
                e1.printStackTrace();
                break;
            }
        }

        if (reconnected)
        {
            System.out.println("Client " + this.host + ":" + this.port + " reconnect successfull after " + this.maxReconnectAttempts + " attempts.");
            this.eventDispatcher.dispatch(new ReconnectSuccessfull(this));
        }
        else
        {
            System.err.println("Client " + this.host + ":" + this.port + " failed to reconnect after " + this.maxReconnectAttempts + " attempts.");
            this.eventDispatcher.dispatch(new ReconnectFailed(this));
            kill();
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
        boolean error = false;

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
                if (this.running)
                {
                    System.err.println("KeepAlive acknowledge took longer than " + this.keepAliveTimeout + " ms. Client " + this.host + ":" + this.port);
                    this.eventDispatcher.dispatch(new ConnectionLost(this));
                    error = true;
                    this.running = false;
                    break;
                }
            }
            catch (SocketException sok)
            {
                if (this.running)
                {
                    System.err.println("Connection broken. Client " + this.host + ":" + this.port);
                    this.eventDispatcher.dispatch(new ConnectionLost(this));
                    error = true;
                    this.running = false;
                    break;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        if (error)
        {
            if (this.autoReconnect)
            {
                reconnect();
            }
            else
            {
                kill();
            }
        }
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        boolean error = false;

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
                if (this.running)
                {
                    System.err.println("Connection lost. Client " + this.host + ":" + this.port);
                    this.eventDispatcher.dispatch(new ConnectionLost(this));
                    error = true;
                    this.running = false;
                    break;
                }
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

        if (error)
        {
            if (this.autoReconnect)
            {
                reconnect();
            }
            else
            {
                kill();
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