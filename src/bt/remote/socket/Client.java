package bt.remote.socket;

import bt.async.Async;
import bt.async.AsyncException;
import bt.async.AsyncManager;
import bt.async.Data;
import bt.remote.socket.data.*;
import bt.remote.socket.evnt.*;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;
import bt.utils.StringID;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;

/**
 * A class wrapping a {@link Socket}. This class should be used on client side in a client-server connection.
 *
 * @author &#8904
 */
public class Client implements Killable, Runnable
{
    /** The socket used to communicate with the server. */
    protected Socket socket;

    /** The stream for incoming objects. */
    protected ObjectInputStream in;

    /** The stream for outgoing objects. */
    protected ObjectOutputStream out;

    /** A processor for incoming data from requests. */
    protected DataProcessor dataProcessor;

    /** A flag to indicate if this client is currently or should be running (=waiting for incoming messages). */
    protected volatile boolean running;

    /** The hostname of the server that this client is connected to. */
    protected String host;

    /** The port of the server that this client is connected to. */
    protected int port;

    /** The current latency. */
    protected long currentPing;

    /**
     * The time between keepalives in milliseconds. This is also the time that is waited for a keepalive response before
     * deeming the connection as broken.
     */
    protected long keepAliveTimeout = 10000;

    /** A dispatcher to distribute client related events. */
    protected Dispatcher eventDispatcher;

    /** Indicates that this client should attempt reconnecting if the connection is lost. */
    protected boolean autoReconnect;

    /** The maximum number of reconnect attempts before giving up. -1 for infinite attempts. */
    protected int maxReconnectAttempts = -1;

    /**
     * Creates a new instance, initializes the {@link #eventDispatcher} and adds the instance to the
     * {@link InstanceKiller}.
     */
    protected Client()
    {
        this.eventDispatcher = new Dispatcher();
        InstanceKiller.killOnShutdown(this);
    }

    /**
     * Creates a new instance with the given hostname and port of the desired server.
     *
     * <p>
     * This constructor will not create an actual {@link Socket} yet, for that the {@link Client#start()} method needs
     * to be called.
     * </p>
     *
     * @param host
     * @param port
     * @throws IOException
     */
    public Client(String host, int port) throws IOException
    {
        this();
        this.host = host;
        this.port = port;
    }

    protected void setupConnection() throws IOException
    {
        this.socket = new Socket(this.host, this.port);
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

    /**
     * Gets the {@link Dispatcher} used to ditribute events of the client.
     *
     * <p>
     * Possible events:
     * <ul>
     * <li>{@link PingUpdate}</li>
     * <li>{@link ConnectionLost}</li>
     * <li>{@link ReconnectStarted}</li>
     * <li>{@link ReconnectFailed}</li>
     * <li>{@link ReconnectSuccessfull}</li>
     * </ul>
     * </p>
     *
     * @return
     */
    public Dispatcher getEventDispatcher()
    {
        return this.eventDispatcher;
    }

    /**
     * Sets a processor which will receive the data for every incoming request.
     *
     * @param dataProcessor
     */
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
        int attempts = 0;
        this.eventDispatcher.dispatch(new ReconnectStarted(this));
        closeResources();

        while (attempts < this.maxReconnectAttempts || this.maxReconnectAttempts == -1)
        {
            attempts ++ ;
            System.out.println("Client " + this.host + ":" + this.port + " attempting to reconnect (attempt: " + attempts + ")");

            try
            {
                this.running = true;
                setupConnection();
                startThreads();
                reconnected = true;
                break;
            }
            catch (ConnectException e)
            {
                System.err.println("Client " + this.host + ":" + this.port + " reconnect attempt " + attempts + " failed.");
            }
            catch (IOException e1)
            {
                e1.printStackTrace();
                break;
            }
        }

        if (reconnected)
        {
            System.out.println("Client " + this.host + ":" + this.port + " reconnect successfull after " + attempts + " attempts.");
            this.eventDispatcher.dispatch(new ReconnectSuccessfull(this));
        }
        else
        {
            System.err.println("Client " + this.host + ":" + this.port + " failed to reconnect after " + attempts + " attempts.");
            this.eventDispatcher.dispatch(new ReconnectFailed(this));
            kill();
        }
    }

    public void start() throws IOException
    {
        this.running = true;

        try
        {
            setupConnection();
            startThreads();
        }
        catch (ConnectException e)
        {
            System.err.println("Failed to connect to " + this.host + ":" + this.port + ".");
            this.running = false;

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

    protected void startThreads()
    {
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
                this.eventDispatcher.dispatch(new PingUpdate(this.currentPing));
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

    public boolean isConnected()
    {
        return this.socket != null && this.socket.isConnected() && !this.socket.isClosed();
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
     * @return the port
     */
    public int getPort()
    {
        return this.port;
    }

    /**
     * Gets the time between keepalives in milliseconds. This is also the time that is waited for a keepalive response
     * before deeming the connection as broken.
     *
     * @return the keepAliveTimeout
     */
    public long getKeepAliveTimeout()
    {
        return this.keepAliveTimeout;
    }

    /**
     * Sets the time between keepalives in milliseconds. This is also the time that is waited for a keepalive response
     * before deeming the connection as broken.
     *
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