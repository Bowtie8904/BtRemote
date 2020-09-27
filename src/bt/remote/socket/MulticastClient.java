package bt.remote.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.util.function.Consumer;

import bt.log.Logger;
import bt.runtime.InstanceKiller;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

/**
 * @author &#8904
 *
 */
public class MulticastClient implements Killable, Runnable
{
    public static final String DEFAULT_GROUP_ADDRESS = "230.0.0.0";
    public static final int DEFAULT_PORT = 9000;
    protected MulticastSocket socket = null;
    protected Consumer<DatagramPacket> receiver;
    protected boolean running;
    protected int port;
    protected InetAddress multicastGroup;

    public MulticastClient(int port, String multicastGroupAddress) throws IOException
    {
        this.socket = new MulticastSocket();
        SocketAddress sockAddr = new InetSocketAddress(port);
        this.socket.bind(sockAddr);
        this.multicastGroup = InetAddress.getByName(multicastGroupAddress);
        this.socket.joinGroup(this.multicastGroup);
        this.socket.setTimeToLive(64);
    }

    public void start()
    {
        Logger.global().print("Starting MulticastClient " + this.socket.getInetAddress().getHostAddress() + ":" + this.socket.getLocalPort());
        this.running = true;
        Threads.get().execute(this, "MulticastClient " + this.socket.getInetAddress().getHostAddress() + ":" + this.socket.getLocalPort());
    }

    public void onReceive(Consumer<DatagramPacket> receiver)
    {
        this.receiver = receiver;
    }

    public void send(DatagramPacket packet) throws IOException
    {
        this.socket.send(packet);
    }

    /**
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        Logger.global().print("Killing MulticastClient " + this.socket.getInetAddress().getHostAddress() + ":" + this.socket.getLocalPort());
        this.running = false;
        Null.checkRun(this.socket, () -> Exceptions.ignoreThrow(() -> this.socket.leaveGroup(this.multicastGroup)));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.socket));

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        byte[] buf = new byte[512];

        while (this.running)
        {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try
            {
                this.socket.receive(packet);
                Null.checkConsume(this.receiver, packet);
            }
            catch (IOException e)
            {
                Logger.global().print(e);
            }
        }
    }
}