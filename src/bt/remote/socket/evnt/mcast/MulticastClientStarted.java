package bt.remote.socket.evnt.mcast;

import bt.remote.socket.MulticastClient;

public class MulticastClientStarted extends MulticastClientEvent
{
    public MulticastClientStarted(MulticastClient client)
    {
        super(client);
    }
}