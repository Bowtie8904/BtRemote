package bt.remote.socket;

import java.io.IOException;
import java.net.Socket;

import bt.utils.Null;

/**
 * @author &#8904
 *
 */
public class ServerClient extends Client
{
    protected Server server;

    public ServerClient(Socket socket) throws IOException
    {
        super(socket);
    }

    public ServerClient(String host, int port) throws IOException
    {
        super(host, port);
    }

    /**
     * @return the server
     */
    public Server getServer()
    {
        return this.server;
    }

    /**
     * @param server
     *            the server to set
     */
    public void setServer(Server server)
    {
        this.server = server;
    }

    /**
     * @see bt.types.Killable#kill()
     */
    @Override
    public void kill()
    {
        super.kill();
        Null.checkRun(this.server, () -> this.server.removeClient(this));
    }
}