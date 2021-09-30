package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientReconnectSuccessfull extends ClientEvent
{
    /**
     * @param client
     */
    public ClientReconnectSuccessfull(Client client)
    {
        super(client);
    }
}