package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;

public class ServerClientKilled extends ServerClientEvent
{
    public ServerClientKilled(Server server, ServerClient client)
    {
        super(server, client);
    }
}