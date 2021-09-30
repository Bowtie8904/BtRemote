package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;

public class ServerKilled extends ServerEvent
{
    public ServerKilled(Server server)
    {
        super(server);
    }
}