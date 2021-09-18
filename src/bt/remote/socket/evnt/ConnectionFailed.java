package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ConnectionFailed extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ConnectionFailed(Client client, Exception e)
    {
        super(client, e);
    }
}