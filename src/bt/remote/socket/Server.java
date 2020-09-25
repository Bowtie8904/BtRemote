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
    protected ServerSocket serverSocket;
    protected Dispatcher eventDispatcher;
    protected boolean running;

    public Server(int port) throws IOException
    {
        this.eventDispatcher = new Dispatcher();
        this.serverSocket = new ServerSocket(port);
        InstanceKiller.killOnShutdown(this);
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
    }

    public void start()
    {
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