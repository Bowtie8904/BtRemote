package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ReconnectSuccessfull extends ClientEvent
{
    /**
     * @param client
     */
    public ReconnectSuccessfull(Client client)
    {
        super(client);
    }
}