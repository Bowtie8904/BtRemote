package bt.remote.socket;

import bt.remote.socket.data.*;
import bt.remote.socket.evnt.UnspecifiedException;
import bt.scheduler.Threads;
import bt.utils.Exceptions;
import bt.utils.Null;

import java.io.*;
import java.util.Arrays;

public class RawClient extends Client
{
    /**
     * The stream for incoming data.
     */
    protected DataInputStream in;

    /**
     * The stream for outgoing data.
     */
    protected OutputStream out;

    /**
     * A processor for incoming data.
     */
    protected ByteProcessor byteProcessor;

    /**
     * The implementation of the data reading.
     */
    protected RawDataReader reader;

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
    public RawClient(String host, int port)
    {
        super(host, port);
        setupDefaultDataReader();
    }

    protected RawClient()
    {
        super();
    }

    protected void setupDefaultDataReader()
    {
        setDataReader(in -> {
            byte[] data = new byte[4096];
            int bytes = 0;
            int messageLength = 0;

            bytes = in.read(data);
            data = Arrays.copyOf(data, bytes);


            return data;
        });
    }

    /**
     * Sets the instance that will be called to read data from the given stream.
     *
     * @param reader
     */
    public void setDataReader(RawDataReader reader)
    {
        this.reader = reader;
    }

    /**
     * Sets a processor which will receive the data for every incoming message.
     *
     * @param dataProcessor
     */
    public void setByteProcessor(ByteProcessor byteProcessor)
    {
        this.byteProcessor = byteProcessor;
    }

    @Override
    protected void setupConnection() throws IOException
    {
        super.setupConnection();
        this.out = this.socket.getOutputStream();
        this.out.flush();
        this.in = new DataInputStream(new BufferedInputStream(this.socket.getInputStream()));
    }

    protected void handleData(byte[] data)
    {
        byte[] ret = null;

        if (this.byteProcessor != null)
        {
            ret = this.byteProcessor.process(data);
        }

        if (ret != null)
        {
            send(ret);
        }
    }

    protected synchronized void send(byte[] data)
    {
        try
        {
            this.out.write(data);
        }
        catch (IOException e)
        {
            dispatchExceptionEvent(new UnspecifiedException(this, e), false);
        }
    }

    @Override
    protected void readData() throws IOException
    {
        if (this.reader != null)
        {
            byte[] data = this.reader.read(this.in);

            if (this.singleThreadProcessing)
            {
                handleData(data);
            }
            else
            {
                Threads.get().executeCached(() -> handleData(data));
            }
        }
    }

    @Override
    protected void closeResources()
    {
        Exceptions.ignoreThrow(() -> Null.checkClose(this.in));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.out));
        super.closeResources();
    }
}