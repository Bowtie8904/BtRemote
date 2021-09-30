package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;

/**
 * Indicates that a new client has connected to a server.
 *
 * @author &#8904
 */
public class NewClientConnection extends ServerClientEvent
{
    public NewClientConnection(Server server, ServerClient client)
    {
        super(server, client);
    }
}