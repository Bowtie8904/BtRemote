package bt.remote.socket.evnt;

import bt.remote.socket.Client;

public class UnspecifiedException extends ClientExceptionEvent
{
    /**
     * @param client
     * @param e
     */
    public UnspecifiedException(Client client, Exception e)
    {
        super(client, e);
    }
}