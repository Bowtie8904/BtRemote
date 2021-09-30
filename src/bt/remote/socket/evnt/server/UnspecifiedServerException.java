package bt.remote.socket.evnt.server;

import bt.remote.socket.Server;

public class UnspecifiedServerException extends ServerExceptionEvent
{
    public UnspecifiedServerException(Server server, Exception e)
    {
        super(server, e);
    }
}