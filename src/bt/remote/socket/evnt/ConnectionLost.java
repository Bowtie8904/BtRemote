package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ConnectionLost extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ConnectionLost(Client client, Exception e)
    {
        super(client, e);
    }
}