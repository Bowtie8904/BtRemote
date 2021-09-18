package bt.remote.socket;

import bt.remote.socket.evnt.*;
import bt.remote.socket.exc.WrappedClientException;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;

/**
 * A class wrapping a {@link Socket}. This class should be used on client side in a client-server connection.
 *
 * @author &#8904
 */
public abstract class Client implements Killable, Runnable
{
    /** The socket used to communicate with the server. */
    protected Socket socket;

    /** A flag to indicate if this client is currently or should be running (=waiting for incoming messages). */
    protected volatile boolean running;

    /** The hostname of the server that this client is connected to. */
    protected String host;

    /** The port of the server that this client is connected to. */
    protected int port;

    /** A dispatcher to distribute client related events. */
    protected Dispatcher eventDispatcher;

    /** Indicates that this client should attempt reconnecting if the connection is lost. */
    protected boolean autoReconnect;

    /** The maximum number of reconnect attempts before giving up. -1 for infinite attempts. */
    protected int maxReconnectAttempts = -1;

    /** Inidcates whether data should be processed sequencially instead of in parallel. */
    protected boolean singleThreadProcessing = false;

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
    public Client(String host, int port)
    {
        this();
        this.host = host;
        this.port = port;
    }

    protected void setupConnection() throws IOException
    {
        this.socket = new Socket(this.host, this.port);
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
     * <li>{@link PingUpdate} if the client has a new value for ping. Might not be supported by all clients</li>
     * <li>{@link KeepAliveTimeout} if a sent keep alive message was not answered in time. Might not be supported by all clients</li>
     * <li>{@link ConnectionFailed} initial connection to the host failed</li>
     * <li>{@link ConnectionLost} previously established connection was lost</li>
     * <li>{@link ReconnectStarted} reconnect efforts were started</li>
     * <li>{@link ReconnectFailed} reconnect efforts failed</li>
     * <li>{@link ReconnectSuccessfull} reconnect efforts were successful</li>
     * <li>{@link ReconnectAttempt} a specific reconnect attempt was started</li>
     * <li>{@link ReconnectAttemptFailed} a specific reconnect attempt failed</li>
     * <li>{@link ClientKilled} the client was destroyed through a call to {@link #kill()}</li>
     * <li>{@link UnspecifiedException} less specific exception was thrown somewhere in this class</li>
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
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        closeResources();

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }

        this.eventDispatcher.dispatch(new ClientKilled(this));
    }

    protected void closeResources()
    {
        this.running = false;
        Exceptions.ignoreThrow(() -> Null.checkClose(this.socket));
    }

    protected void reconnect()
    {
        boolean reconnected = false;
        int attempts = 0;
        this.eventDispatcher.dispatch(new ReconnectStarted(this));
        closeResources();
        Exception failureReason = null;

        while (attempts < this.maxReconnectAttempts || this.maxReconnectAttempts == -1)
        {
            attempts ++ ;
            this.eventDispatcher.dispatch(new ReconnectAttempt(this, attempts, this.maxReconnectAttempts));

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
                dispatchExceptionEvent(new ReconnectAttemptFailed(this, e, attempts, this.maxReconnectAttempts), false);
                failureReason = e;
            }
            catch (IOException e1)
            {
                dispatchExceptionEvent(new ReconnectAttemptFailed(this, e1, attempts, this.maxReconnectAttempts), false);
                failureReason = e1;
                break;
            }
        }

        if (reconnected)
        {
            this.eventDispatcher.dispatch(new ReconnectSuccessfull(this));
        }
        else
        {
            dispatchExceptionEvent(new ReconnectFailed(this, failureReason), true);
            kill();
        }
    }

    public void start()
    {
        this.running = true;

        try
        {
            setupConnection();
            startThreads();
        }
        catch (IOException e)
        {
            this.running = false;
            dispatchExceptionEvent(new ConnectionFailed(this, e), true);
            kill();
        }
    }

    protected void startThreads()
    {
        Threads.get().execute(this, "Client " + this.host + ":" + this.port);
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
                readData();
            }
            catch (EOFException eof)
            {
                if (this.running)
                {
                    dispatchExceptionEvent(new ConnectionLost(this, eof), true);
                    error = true;
                    this.running = false;
                    break;
                }
            }
            catch (IOException io)
            {
                dispatchExceptionEvent(new UnspecifiedException(this, io), false);
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

    protected void dispatchExceptionEvent(ClientExceptionEvent event, boolean requiresHandling)
    {
        int dispatched = this.eventDispatcher.dispatch(event);

        if (requiresHandling && dispatched == 0)
        {
            throw new WrappedClientException(event.getException());
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

    public boolean isSingleThreadProcessing()
    {
        return singleThreadProcessing;
    }

    public void setSingleThreadProcessing(boolean singleThreadProcessing)
    {
        this.singleThreadProcessing = singleThreadProcessing;
    }

    protected abstract void readData() throws IOException;
}