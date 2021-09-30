package bt.remote.socket;

import bt.remote.socket.evnt.client.*;
import bt.remote.socket.exc.WrappedException;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

import java.io.*;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;

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
     * <li>{@link ClientPingUpdate} if the client has a new value for ping. Might not be supported by all clients</li>
     * <li>{@link ClientKeepAliveTimeout} if a sent keep alive message was not answered in time. Might not be supported by all clients</li>
     * <li>{@link ClientConnectionSuccessfull} initial connection to the host succeeded</li>
     * <li>{@link ClientConnectionFailed} initial connection to the host failed</li>
     * <li>{@link ClientConnectionLost} previously established connection was lost</li>
     * <li>{@link ClientReconnectStarted} reconnect efforts were started</li>
     * <li>{@link ClientReconnectFailed} reconnect efforts failed</li>
     * <li>{@link ClientReconnectSuccessfull} reconnect efforts were successful</li>
     * <li>{@link ClientReconnectAttempt} a specific reconnect attempt was started</li>
     * <li>{@link ClientReconnectAttemptFailed} a specific reconnect attempt failed</li>
     * <li>{@link ClientKilled} the client was destroyed through a call to {@link #kill()}</li>
     * <li>{@link UnspecifiedClientException} less specific exception was thrown somewhere in this class</li>
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

    protected synchronized void reconnect()
    {
        boolean reconnected = false;
        int attempts = 0;
        this.eventDispatcher.dispatch(new ClientReconnectStarted(this));
        closeResources();
        Exception failureReason = null;

        while (attempts < this.maxReconnectAttempts || this.maxReconnectAttempts == -1)
        {
            attempts ++ ;
            this.eventDispatcher.dispatch(new ClientReconnectAttempt(this, attempts, this.maxReconnectAttempts));

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
                dispatchExceptionEvent(new ClientReconnectAttemptFailed(this, e, attempts, this.maxReconnectAttempts), false);
                failureReason = e;
            }
            catch (IOException e1)
            {
                dispatchExceptionEvent(new ClientReconnectAttemptFailed(this, e1, attempts, this.maxReconnectAttempts), false);
                failureReason = e1;
                break;
            }
        }

        if (reconnected)
        {
            this.eventDispatcher.dispatch(new ClientReconnectSuccessfull(this));
        }
        else
        {
            dispatchExceptionEvent(new ClientReconnectFailed(this, failureReason), true);
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
            this.eventDispatcher.dispatch(new ClientConnectionSuccessfull(this));
        }
        catch (IOException e)
        {
            this.running = false;
            dispatchExceptionEvent(new ClientConnectionFailed(this, e), true);
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
                    dispatchExceptionEvent(new ClientConnectionLost(this, eof), false);
                    error = true;
                    this.running = false;
                    break;
                }
            }
            catch (SocketException e)
            {
                if (this.running)
                {
                    dispatchExceptionEvent(new ClientConnectionLost(this, e), false);
                    this.running = false;
                    kill();
                    break;
                }
            }
            catch (IOException io)
            {
                dispatchExceptionEvent(new UnspecifiedClientException(this, io), false);
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
            throw new WrappedException(event.getException());
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