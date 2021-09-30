package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

public class ClientKeepAliveTimeout extends ClientExceptionEvent
{
    private long exceededTimeout;

    /**
     * @param client
     * @param e
     */
    public ClientKeepAliveTimeout(Client client, Exception e, long exceededTimeout)
    {
        super(client, e);
        this.exceededTimeout = exceededTimeout;
    }

    public long getExceededTimeout()
    {
        return exceededTimeout;
    }

    public void setExceededTimeout(long exceededTimeout)
    {
        this.exceededTimeout = exceededTimeout;
    }
}