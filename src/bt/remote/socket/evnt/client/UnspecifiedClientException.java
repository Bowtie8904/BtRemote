package bt.remote.socket.evnt.client;

import bt.remote.socket.Client;

public class UnspecifiedClientException extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public UnspecifiedClientException(Client client, Exception e)
    {
        super(client, e);
    }
}