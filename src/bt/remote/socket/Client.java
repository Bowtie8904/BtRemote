package bt.remote.socket;

import bt.remote.socket.evnt.*;
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
    public Client(String host, int port) throws IOException
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
                    System.err.println("Connection lost. Client " + this.host + ":" + this.port);
                    this.eventDispatcher.dispatch(new ConnectionLost(this));
                    error = true;
                    this.running = false;
                    break;
                }
            }
            catch (IOException io)
            {
                io.printStackTrace();
                // ignore
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