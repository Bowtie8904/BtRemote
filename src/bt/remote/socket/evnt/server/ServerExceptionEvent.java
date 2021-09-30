package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;

public class ServerExceptionEvent extends ServerEvent
{
    private Exception e;

    public ServerExceptionEvent(Server server, Exception e)
    {
        super(server);
    }

    public Exception getException()
    {
        return e;
    }

    public void setException(Exception e)
    {
        this.e = e;
    }
}