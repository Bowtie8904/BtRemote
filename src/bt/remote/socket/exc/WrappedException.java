package bt.remote.socket.exc;

public class WrappedException extends RuntimeException
{
    public WrappedException(Exception e)
    {
        super(e);
    }
}