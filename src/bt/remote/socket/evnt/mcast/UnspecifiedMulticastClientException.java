package bt.remote.socket.evnt.mcast;

import bt.remote.socket.MulticastClient;

public class UnspecifiedMulticastClientException extends MulticastClientExceptionEvent
{
    public UnspecifiedMulticastClientException(MulticastClient client, Exception e)
    {
        super(client, e);
    }
}