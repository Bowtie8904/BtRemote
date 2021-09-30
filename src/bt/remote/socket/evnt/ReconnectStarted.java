package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ReconnectStarted extends ClientEvent
{
    /**
     * @param client
     */
    public ReconnectStarted(Client client)
    {
        super(client);
    }
}