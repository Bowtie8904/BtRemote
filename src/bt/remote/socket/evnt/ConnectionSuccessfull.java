package bt.remote.socket.evnt;

import bt.remote.socket.Client;

/**
 * @author &#8904
 *
 */
public class ConnectionSuccessfull extends ClientEvent
{
    /**
     * @param client
     */
    public ConnectionSuccessfull(Client client)
    {
        super(client);
    }
}