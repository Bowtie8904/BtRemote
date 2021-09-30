package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientConnectionLost extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ClientConnectionLost(Client client, Exception e)
    {
        super(client, e);
    }
}