package bt.remote.socket.evnt.mcast;

import bt.remote.socket.MulticastClient;

public class MulticastClientKilled extends MulticastClientEvent
{
    public MulticastClientKilled(MulticastClient client)
    {
        super(client);
    }
}