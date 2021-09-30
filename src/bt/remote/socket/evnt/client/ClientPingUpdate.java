package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

public class ClientPingUpdate extends ClientEvent
{
    private long ping;

    public ClientPingUpdate(Client client, long ping)
    {
        super(client);
    }

    /**
     * @return the ping
     */
    public long getPing()
    {
        return this.ping;
    }
}