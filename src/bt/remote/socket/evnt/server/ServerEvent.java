package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;

public class ServerEvent
{
    protected Server server;

    public ServerEvent(Server server)
    {
        this.server = server;
    }

    public Server getServer()
    {
        return server;
    }
}