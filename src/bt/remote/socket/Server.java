package bt.remote.socket;

import java.io.IOException;
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
    protected String name;
    protected ServerSocket serverSocket;
    protected MulticastClient multicastClient;
    protected Dispatcher eventDispatcher;
    protected boolean running;

    public Server(int port) throws IOException
    {
        this.eventDispatcher = new Dispatcher();
        this.serverSocket = new ServerSocket(port);
        this.name = "";
        InstanceKiller.killOnShutdown(this);
    }

    public void setupMultiCastDiscovering() throws IOException
    {
        setupMultiCastDiscovering(MulticastClient.DEFAULT_PORT);
    }

    public void setupMultiCastDiscovering(int port) throws IOException
    {
        setupMultiCastDiscovering(MulticastClient.DEFAULT_GROUP_ADDRESS, port);
    }

    public void setupMultiCastDiscovering(String multicastGroupAdress, int port) throws IOException
    {
        this.multicastClient = new MulticastClient(port, multicastGroupAdress);
        this.multicastClient.onMulticastReceive(packet ->
        {
            String message = new String(packet.getData());

            if (message.trim().equalsIgnoreCase("discover"))
            {
                try
                {
                    this.multicastClient.send(this.name + " [" + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort() + "]");
                }
                catch (IOException e)
                {
                    Logger.global().print(e);
                }
            }
        });

        if (this.running)
        {
            this.multicastClient.start();
        }
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
        Logger.global().print("Killing server " + this.name + " [" + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort() + "]");
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
        Logger.global().print("Starting server " + this.name + " [" + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort() + "]");
        this.running = true;
        Threads.get().execute(this, "Server " + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
        this.multicastClient.start();
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

    /**
     * @return the name
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @param name
     *            the name to set
     */
    public void setName(String name)
    {
        this.name = name;
    }
}