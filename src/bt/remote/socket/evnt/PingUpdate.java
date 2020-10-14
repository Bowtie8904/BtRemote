package bt.remote.socket.evnt;

/**
 * @author &#8904
 *
 */
public class PingUpdate
{
    private long ping;

    public PingUpdate(long ping)
    {
        this.ping = ping;
    }

    /**
     * @return the ping
     */
    public long getPing()
    {
        return this.ping;
    }
}