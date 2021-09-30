package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientReconnectStarted extends ClientEvent
{
    /**
     * @param client
     */
    public ClientReconnectStarted(Client client)
    {
        super(client);
    }
}