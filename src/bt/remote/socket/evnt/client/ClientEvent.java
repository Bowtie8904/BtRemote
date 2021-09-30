package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

public class ClientEvent
{
    private Client client;

    /**
     * @param client
     */
    public ClientEvent(Client client)
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