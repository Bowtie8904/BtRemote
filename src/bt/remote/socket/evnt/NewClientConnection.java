package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class NewClientConnection
{
    private Client client;

    /**
     * @param client
     */
    public NewClientConnection(Client client)
    {
        this.client = client;
    }

    /**
     * @return the client
     */
    public Client getClient()
    {
        return this.client;
    }

    /**
     * @param client
     *            the client to set
     */
    public void setClient(Client client)
    {
        this.client = client;
    }
}