package bt.remote.socket.evnt;

import bt.remote.socket.ServerClient;

/**
 * Indicates that a new client has connected to a server.
 *
 * @author &#8904
 */
public class NewClientConnection
{
    private ServerClient client;

    /**
     * @param client
     */
    public NewClientConnection(ServerClient client)
    {
        this.client = client;
    }

    /**
     * @return the client
     */
    public ServerClient getClient()
    {
        return this.client;
    }

    /**
     * @param client
     *            the client to set
     */
    public void setClient(ServerClient client)
    {
        this.client = client;
    }
}