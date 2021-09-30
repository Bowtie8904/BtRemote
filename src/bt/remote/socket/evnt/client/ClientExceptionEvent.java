package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

public class ClientExceptionEvent extends ClientEvent
{
    private Exception e;

    /**
     * @param client
     */
    public ClientExceptionEvent(Client client, Exception e)
    {
        super(client);
        this.e = e;
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