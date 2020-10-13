package bt.remote.socket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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
        super();
        this.socket = socket;
    }

    @Override
    protected void setupConnection() throws IOException
    {
        this.host = this.socket.getInetAddress().getHostAddress();
        this.port = this.socket.getPort();
        this.out = new ObjectOutputStream(this.socket.getOutputStream());
        this.out.flush();
        this.in = new ObjectInputStream(this.socket.getInputStream());
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