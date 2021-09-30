package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;
import bt.remote.socket.ServerClient;

public class ServerClientEvent extends ServerEvent
{
    protected ServerClient client;

    public ServerClientEvent(Server server, ServerClient client)
    {
        super(server);
        this.client = client;
    }

    public ServerClient getClient()
    {
        return client;
    }
}