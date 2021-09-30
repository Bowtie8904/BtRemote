package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;

public class ServerStarted extends ServerEvent
{
    public ServerStarted(Server server)
    {
        super(server);
    }
}