package bt.remote.socket.evnt;

import bt.remote.socket.ServerClient;

/**
 * @author &#8904
 *
 */
public class RemovedClientConnection
{
    private ServerClient client;

    /**
     * @param client
     */
    public RemovedClientConnection(ServerClient client)
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