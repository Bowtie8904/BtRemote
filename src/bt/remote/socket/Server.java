package bt.remote.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import bt.remote.socket.evnt.NewClientConnection;
import bt.remote.socket.evnt.RemovedClientConnection;
import bt.runtime.InstanceKiller;
import bt.runtime.evnt.Dispatcher;
import bt.scheduler.Threads;
import bt.types.Killable;
import bt.utils.Exceptions;
import bt.utils.Null;

/**
 * A class wrapping a {@link ServerSocket}. The main task for this class is to handle incoming connections.
 *
 * <p>
 * To customize which type of clients will be created on server side you can override
 * {@link Server#createClient(Socket)}.
 * </p>
 *
 * @author &#8904
 */
public class Server implements Killable, Runnable
{
    /** A name/description of this server. Used to give information via multicast if enabled. */
    protected String name;

    /** The hostname that this server is bound to. */
    protected String host;

    /** The socket used to receive connections. */
    protected ServerSocket serverSocket;

    /** The optional client to receive and reply to multicast datagrams. */
    protected MulticastClient multicastClient;

    /** A dispatcher to distribute server related events such as {@link NewClientConnection}. */
    protected Dispatcher eventDispatcher;

    /** A list of all currently connected clients. */
    protected List<ServerClient> clients;

    /** A flag to indicate if this server is currently or should be running (=waiting for new connections). */
    protected volatile boolean running;

    /**
     * Creates a new server and binds it to localhost and the given port.
     *
     * @param port
     *            The port to listen on.
     * @throws IOException
     */
    public Server(int port) throws IOException
    {
        InstanceKiller.killOnShutdown(this);
        this.eventDispatcher = new Dispatcher();
        this.serverSocket = new ServerSocket(port);
        this.clients = new CopyOnWriteArrayList<>();
        this.name = "";
        this.host = InetAddress.getLocalHost().getHostName();
    }

    /**
     * Sets up a {@link MulticastClient} using {@link MulticastClient#DEFAULT_GROUP_ADDRESS} and
     * {@link MulticastClient#DEFAULT_PORT}.
     *
     * <p>
     * The client will listen for incoming multicast discover messages and respond with its name + host and port. <br>
     * For the client to respond the incoming message must only contain the text "discover".
     * </p>
     *
     * @throws IOException
     * @see {@link Server#setupMultiCastDiscovering(int)}
     * @see {@link Server#setupMultiCastDiscovering(String, int)}
     */
    public void setupMultiCastDiscovering() throws IOException
    {
        setupMultiCastDiscovering(MulticastClient.DEFAULT_PORT);
    }

    /**
     * Sets up a {@link MulticastClient} using {@link MulticastClient#DEFAULT_GROUP_ADDRESS} and the given port.
     *
     * <p>
     * The client will listen for incoming multicast discover messages and respond with its name + host and port. <br>
     * For the client to respond the incoming message must only contain the text "discover".
     * </p>
     *
     * @throws IOException
     * @see {@link Server#setupMultiCastDiscovering()}
     * @see {@link Server#setupMultiCastDiscovering(String, int)}
     */
    public void setupMultiCastDiscovering(int port) throws IOException
    {
        setupMultiCastDiscovering(MulticastClient.DEFAULT_GROUP_ADDRESS, port);
    }

    /**
     * Sets up a {@link MulticastClient} using the given multicast address and port.
     *
     * <p>
     * The client will listen for incoming multicast discover messages and respond with its name + host and port. <br>
     * For the client to respond the incoming message must only contain the text "discover".
     * </p>
     *
     * @throws IOException
     * @see {@link Server#setupMultiCastDiscovering(int)}
     * @see {@link Server#setupMultiCastDiscovering()}
     */
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
                    this.multicastClient.send(this.name + " [" + this.host + ":" + this.serverSocket.getLocalPort() + "]");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        });

        // we dont want to start the mcast client unless the entire server has already been started via start()
        if (this.running)
        {
            this.multicastClient.start();
        }
    }

    /**
     * Waits for a new connection attempt.
     *
     * @return
     * @throws IOException
     */
    protected boolean awaitConnection() throws IOException
    {
        boolean connected = false;

        if (!this.serverSocket.isClosed())
        {
            Socket socket = this.serverSocket.accept();
            ServerClient client = createClient(socket);
            System.out.println("New client connection " + client.getHost() + ":" + client.getPort());
            client.setServer(this);
            this.clients.add(client);
            this.eventDispatcher.dispatch(new NewClientConnection(client));
            client.start();

            connected = true;
        }

        return connected;
    }

    /**
     * Called by {@link Server#awaitConnection()} whenever a new connection is established.
     *
     * <p>
     * The default implementation of this method simply creates and returns a new instance of {@link ServerClient}.
     * </p>
     *
     * @param socket
     * @return
     * @throws IOException
     */
    protected ServerClient createClient(Socket socket) throws IOException
    {
        return new ServerClient(socket);
    }

    protected void removeClient(ServerClient client)
    {
        if (this.clients.remove(client))
        {
            this.eventDispatcher.dispatch(new RemovedClientConnection(client));
        }
    }

    public List<ServerClient> getClients()
    {
        return this.clients;
    }

    /**
     * Gets the {@link Dispatcher} used to ditribute events of the server.
     *
     * <p>
     * Possible events:
     * <ul>
     * <li>{@link NewClientConnection}</li>
     * <li>{@link RemovedClientConnection}</li>
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
     * Closes the wrapped {@link ServerSocket} and (if setup) the {@link MulticastClient}.
     *
     * <p>
     * Once this method has been called this Server instance can not be restored. All connected clients will be killed.
     * </p>
     *
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        System.out.println("Killing server " + this.name + " [" + this.host + ":" + this.serverSocket.getLocalPort() + "]");
        this.running = false;

        if (!InstanceKiller.isActive())
        {
            InstanceKiller.unregister(this);

            for (var client : this.clients)
            {
                client.kill();
            }
        }

        Exceptions.ignoreThrow(() -> Null.checkClose(this.serverSocket));
        Null.checkKill(this.multicastClient);
    }

    /**
     * Starts executing this servers {@link Server#run() run} method in a new thread.
     *
     * <p>
     * If {@link Server#setupMultiCastDiscovering() any setupMultiCastDiscovering method} has been called then the
     * created {@link MulticastClient} will also be started via {@link MulticastClient#start()}.
     * </p>
     */
    public void start()
    {
        System.out.println("Starting server " + this.name + " [" + this.host + ":" + this.serverSocket.getLocalPort() + "]");
        this.running = true;
        Threads.get().execute(this, "Server " + this.serverSocket.getInetAddress().getHostAddress() + ":" + this.serverSocket.getLocalPort());
        Null.checkRun(this.multicastClient, () -> this.multicastClient.start());
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
                    e.printStackTrace();
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