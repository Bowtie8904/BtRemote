package bt.remote.socket.evnt.mcast;

import bt.remote.socket.MulticastClient;

public class MulticastClientExceptionEvent extends MulticastClientEvent
{
    private Exception e;

    public MulticastClientExceptionEvent(MulticastClient client, Exception e)
    {
        super(client);
        this.e = e;
    }

    public Exception getException()
    {
        return e;
    }
}