package bt.remote.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.ServerSocket;
import java.net.Socket;

import bt.log.Logger;
import bt.remote.socket.evnt.NewClientConnection;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

/**
 * @author &#8904
 *
 */
public class Server implements Killable, Runnable
{
    protected ServerSocket serverSocket;
    protected MulticastClient multicastClient;
    protected Dispatcher eventDispatcher;
    protected boolean running;

    public Server(int port) throws IOException
    {
        this.eventDispatcher = new Dispatcher();
        this.serverSocket = new ServerSocket(port);
        InstanceKiller.killOnShutdown(this);
    }

    public void setupMultiCastDiscovering(String discoverName) throws IOException
    {
        setupMultiCastDiscovering(discoverName, MulticastClient.DEFAULT_GROUP_ADDRESS, MulticastClient.DEFAULT_PORT);
    }

    public void setupMultiCastDiscovering(String discoverName, String multicastGroupAdress, int port) throws IOException
    {
        this.multicastClient = new MulticastClient(port, multicastGroupAdress);
        this.multicastClient.onReceive(packet ->
        {
            byte[] buf = discoverName.getBytes();
            DatagramPacket response = new DatagramPacket(buf, buf.length, packet.getAddress(), packet.getPort());

            try
            {
                this.multicastClient.send(response);
            }
            catch (IOException e)
            {
                Logger.global().print(e);
            }
        });
    }

    protected boolean awaitConnection() throws IOException
    {
        boolean connected = false;

        if (!this.serverSocket.isClosed())
        {
            Socket socket = this.serverSocket.accept();
            Client client = createClient(socket);
            this.eventDispatcher.dispatch(new NewClientConnection(client));
            client.start();

            connected = true;
        }

        return connected;
    }

    protected Client createClient(Socket socket) throws IOException
    {
        return new Client(socket);
    }

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
        Logger.global().print("Killing server " + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
        this.running = false;
        Exceptions.ignoreThrow(() -> Null.checkClose(this.serverSocket));
        Null.checkKill(this.multicastClient);

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);
        }
    }

    public void start()
    {
        Logger.global().print("Starting server " + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
        this.running = true;
        Threads.get().execute(this, "Server " + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run()
    {
        while (this.running)
        {
            try
            {
                awaitConnection();
            }
            catch (IOException e)
            {
                if (this.running)
                {
                    Logger.global().print(e);
                }
            }
        }
    }
}