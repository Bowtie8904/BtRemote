package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ClientReconnectFailed extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ClientReconnectFailed(Client client, Exception e)
    {
        super(client, e);
    }
}