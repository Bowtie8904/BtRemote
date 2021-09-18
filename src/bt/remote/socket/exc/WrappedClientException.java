package bt.remote.socket.exc;

public class WrappedClientException extends RuntimeException
{
    public WrappedClientException(Exception e)
    {
        super(e);
    }
}