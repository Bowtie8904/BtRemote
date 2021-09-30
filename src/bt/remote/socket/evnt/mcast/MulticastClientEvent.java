package bt.remote.socket.evnt.mcast;

import bt.remote.socket.MulticastClient;

public class MulticastClientEvent
{
    protected MulticastClient client;

    public MulticastClientEvent(MulticastClient client)
    {
        this.client = client;
    }

    public MulticastClient getClient()
    {
        return client;
    }
}