package bt.remote.socket;

import bt.async.Async;
import bt.async.AsyncException;
import bt.async.AsyncManager;
import bt.async.Data;
import bt.remote.socket.data.*;
import bt.remote.socket.evnt.ConnectionLost;
import bt.remote.socket.evnt.KeepAliveTimeout;
import bt.remote.socket.evnt.PingUpdate;
import bt.remote.socket.evnt.UnspecifiedException;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.utils.Exceptions;
import bt.utils.Null;
import bt.utils.StringID;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;

/**
 * Extension of Client for communication through object streams
 */
public class ObjectClient extends Client
{
    /** The stream for incoming objects. */
    protected ObjectInputStream in;

    /** The stream for outgoing objects. */
    protected ObjectOutputStream out;

    protected boolean sendKeepAlives = true;

    /** The current latency. */
    protected long currentPing;

    /** A processor for incoming data from requests. */
    protected DataProcessor dataProcessor;

    /**
     * The time between keepalives in milliseconds. This is also the time that is waited for a keepalive response before
     * deeming the connection as broken.
     */
    protected long keepAliveTimeout = 10000;

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
    public ObjectClient(String host, int port)
    {
        super(host, port);
    }

    protected ObjectClient()
    {
        super();
    }

    /**
     * Sets a processor which will receive the data for every incoming request or generic object.
     *
     * @param dataProcessor
     */
    public void setDataProcessor(DataProcessor dataProcessor)
    {
        this.dataProcessor = dataProcessor;
    }

    @Override
    protected void setupConnection() throws IOException
    {
        super.setupConnection();
        this.out = new ObjectOutputStream(this.socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(this.socket.getInputStream());
    }

    protected void sendKeepAlive()
    {
        boolean error = false;
        boolean keepAliveError = false;
        Exception failureReason = null;

        while (this.running && this.sendKeepAlives && !this.socket.isClosed())
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
                    error = true;
                    keepAliveError = true;
                    this.running = false;
                    failureReason = e;
                    break;
                }
            }
            catch (SocketException sok)
            {
                if (this.running)
                {
                    error = true;
                    this.running = false;
                    failureReason = sok;
                    break;
                }
            }
            catch (IOException e)
            {
                dispatchExceptionEvent(new UnspecifiedException(this, e), false);
            }
        }

        if (error)
        {
            if (this.autoReconnect)
            {
                if (keepAliveError)
                {
                    dispatchExceptionEvent(new KeepAliveTimeout(this, failureReason, this.keepAliveTimeout), false);
                }
                else
                {
                    dispatchExceptionEvent(new ConnectionLost(this, failureReason), false);
                }

                reconnect();
            }
            else
            {
                if (keepAliveError)
                {
                    dispatchExceptionEvent(new KeepAliveTimeout(this, failureReason, this.keepAliveTimeout), true);
                }
                else
                {
                    dispatchExceptionEvent(new ConnectionLost(this, failureReason), true);
                }

                kill();
            }
        }
    }

    protected void handleIncomingRequest(Request request) throws IOException
    {
        Data response = null;

        Object ret = handleData(request.getData());

        if (ret != null)
        {
            response = new Data(ret.getClass(), ret, request.getData().getID());
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

    protected void handleIncomingObject(Object obj) throws IOException
    {
        Object ret = handleData(new Data(obj.getClass(), obj, ""));

        if (ret != null)
        {
            sendObject(ret);
        }
    }

    protected Object handleData(Data data)
    {
        Object ret = null;

        if (this.dataProcessor != null)
        {
            ret = this.dataProcessor.process(data);
        }

        return ret;
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
            dispatchExceptionEvent(new UnspecifiedException(this, e), false);
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
            dispatchExceptionEvent(new UnspecifiedException(this, e), false);
        }
    }

    @Override
    protected void startThreads()
    {
        super.startThreads();

        if (this.sendKeepAlives)
        {
            Threads.get().execute(this::sendKeepAlive, "Ping-Thread " + this.host + ":" + this.port);
        }
    }

    @Override
    protected void readData() throws IOException
    {
        try
        {
            Object incoming = this.in.readObject();

            if (this.singleThreadProcessing)
            {
                dispatchIncomingData(incoming);
            }
            else
            {
                Threads.get().executeCached(() -> dispatchIncomingData(incoming));
            }
        }
        catch (ClassNotFoundException e)
        {
            dispatchExceptionEvent(new UnspecifiedException(this, e), false);
        }
    }

    protected void dispatchIncomingData(Object incoming)
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
            else
            {
                handleIncomingObject(incoming);
            }
        }
        catch (IOException e)
        {
            dispatchExceptionEvent(new UnspecifiedException(this, e), false);
        }
    }

    @Override
    protected void closeResources()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.in));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.out));
        super.closeResources();
    }

    public boolean isSendKeepAlives()
    {
        return sendKeepAlives;
    }

    public void setSendKeepAlives(boolean sendKeepAlives)
    {
        this.sendKeepAlives = sendKeepAlives;
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