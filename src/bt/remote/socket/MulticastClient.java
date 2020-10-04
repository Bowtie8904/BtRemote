package bt.remote.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
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
public class MulticastClient implements Killable
{
    public static final String DEFAULT_GROUP_ADDRESS = "224.0.1.1";
    public static final int DEFAULT_PORT = 9000;
    protected MulticastSocket mcastSocket = null;
    protected Consumer<DatagramPacket> mcastReceiver;
    protected boolean running;
    protected int port;
    protected InetAddress multicastGroup;

    public MulticastClient(int port, String multicastGroupAddress) throws IOException
    {
        InstanceKiller.killOnShutdown(this);
        this.port = port;
        this.mcastSocket = new MulticastSocket(port);
        this.multicastGroup = InetAddress.getByName(multicastGroupAddress);
        this.mcastSocket.joinGroup(this.multicastGroup);
        this.mcastSocket.setTimeToLive(255);
    }

    public void start()
    {
        Logger.global().print("Starting MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
        this.running = true;
        Threads.get().execute(() -> listenForMulticast(), "MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
    }

    public void onMulticastReceive(Consumer<DatagramPacket> receiver)
    {
        this.mcastReceiver = receiver;
    }

    public void send(String msg) throws IOException
    {
        byte[] buf = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(buf, buf.length, this.multicastGroup, this.port);
        send(packet);
    }

    public void send(DatagramPacket packet) throws IOException
    {
        this.mcastSocket.send(packet);
    }

    /**
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        Logger.global().print("Killing MulticastClient " + this.multicastGroup.getHostAddress() + ":" + this.mcastSocket.getLocalPort());
        this.running = false;
        Null.checkRun(this.mcastSocket, () -> Exceptions.ignoreThrow(() -> this.mcastSocket.leaveGroup(this.multicastGroup)));
        Exceptions.ignoreThrow(() -> Null.checkClose(this.mcastSocket));

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    public void listenForMulticast()
    {
        byte[] buf = new byte[512];

        while (this.running)
        {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            try
            {
                this.mcastSocket.receive(packet);
                Null.checkConsume(this.mcastReceiver, packet);
            }
            catch (IOException e)
            {
                Logger.global().print(e);
            }
        }
    }
}