package bt.remote.socket.evnt;

import bt.remote.socket.Client;

public class KeepAliveTimeout extends ClientExceptionEvent
{
    private long exceededTimeout;

    /**
     * @param client
     * @param e
     */
    public KeepAliveTimeout(Client client, Exception e, long exceededTimeout)
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