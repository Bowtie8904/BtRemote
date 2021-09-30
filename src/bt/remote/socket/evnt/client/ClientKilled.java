package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientKilled extends ClientEvent
{
    /**
     * @param client
     */
    public ClientKilled(Client client)
    {
        super(client);
    }
}