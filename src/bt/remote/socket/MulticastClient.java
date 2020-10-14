package bt.remote.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.function.Consumer;

import bt.runtime.InstanceKiller;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

/**
 * A class to wrap a {@link MulticastSocket} to receive and send messages in a multicast environment.
 *
 * @author &#8904
 */
public class MulticastClient implements Killable
{
    /** A default multicast group address. */
    public static final String DEFAULT_GROUP_ADDRESS = "224.0.1.1";

    /** A default port. */
    public static final int DEFAULT_PORT = 9000;

    /** The socket that is used to send and receive multicast datagrams. */
    protected MulticastSocket mcastSocket = null;

    /** A consumer for received {@link DatagramPacket DatagramPackets}. */
    protected Consumer<DatagramPacket> mcastReceiver;

    /** A flag to indicate if this client is currently or should be running (=listening for incoming messages). */
    protected boolean running;

    /** The port that this client is connected to. */
    protected int port;

    /** The multicast group address that this client is connected to. */
    protected InetAddress multicastGroup;

    /**
     * Creates a new instance and attempts to connect to the given address and port.
     *
     * @param port
     * @param multicastGroupAddress
     * @throws IOException
     */
    public MulticastClient(int port, String multicastGroupAddress) throws IOException
    {
        InstanceKiller.killOnShutdown(this);
        this.port = port;
        this.mcastSocket = new MulticastSocket(port);
        this.multicastGroup = InetAddress.getByName(multicastGroupAddress);
        this.mcastSocket.joinGroup(this.multicastGroup);
        this.mcastSocket.setTimeToLive(255);
    }

    /**
     * Starts the {@link MulticastClient#listenForMulticast()} method in a new thread.
     */
    public void start()
    {
        System.out.println("Starting MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
        this.running = true;
        Threads.get().execute(() -> listenForMulticast(), "MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
    }

    /**
     * Sets a consumer that will receive any incoming datagrams.
     *
     * @param receiver
     */
    public void onMulticastReceive(Consumer<DatagramPacket> receiver)
    {
        this.mcastReceiver = receiver;
    }

    /**
     * Sends the given String in a new {@link DatagramPacket}.
     *
     * @param msg
     * @throws IOException
     */
    public void send(String msg) throws IOException
    {
        byte[] buf = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, this.multicastGroup, this.port);
        send(packet);
    }

    /**
     * Sends the given packet.
     *
     * @param packet
     * @throws IOException
     */
    public void send(DatagramPacket packet) throws IOException
    {
        this.mcastSocket.send(packet);
    }

    /**
     * Stops this client and closes the socket
     *
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        System.out.println("Killing MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
        this.running = false;
        Null.checkRun(this.mcastSocket, () -> Exceptions.ignoreThrow(() -> this.mcastSocket.leaveGroup(this.multicastGroup)));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.mcastSocket));

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    /**
     * Waits for incoming messages and gives them to the set {@link MulticastClient#onMulticastReceive(Consumer)
     * datagram consumer}.
     */
    protected void listenForMulticast()
    {
        while (this.running)
        {
            byte[] buf = new byte[512];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try
            {
                this.mcastSocket.receive(packet);
                Null.checkConsume(this.mcastReceiver, packet);
            }
            catch (IOException e)
            {
                // ignore
            }
        }
    }
}