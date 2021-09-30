package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientConnectionSuccessfull extends ClientEvent
{
    /**
     * @param client
     */
    public ClientConnectionSuccessfull(Client client)
    {
        super(client);
    }
}