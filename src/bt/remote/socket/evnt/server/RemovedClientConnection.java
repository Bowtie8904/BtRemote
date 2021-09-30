package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;

/**
 * @author &#8904
 *
 */
public class RemovedClientConnection extends ServerClientEvent
{
    public RemovedClientConnection(Server server, ServerClient client)
    {
        super(server, client);
    }
}