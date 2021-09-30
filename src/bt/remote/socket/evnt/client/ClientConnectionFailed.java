package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;
import bt.remote.socket.evnt.client.ClientExceptionEvent;

/**
 * @author &#8904
 *
 */
public class ClientConnectionFailed extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ClientConnectionFailed(Client client, Exception e)
    {
        super(client, e);
    }
}