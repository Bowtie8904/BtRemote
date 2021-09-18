package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ReconnectFailed extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public ReconnectFailed(Client client, Exception e)
    {
        super(client, e);
    }
}