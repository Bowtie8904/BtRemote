package bt.remote.socket.evnt;

import bt.remote.socket.Client;

public class ReconnectAttemptFailed extends ClientExceptionEvent
{
    private int attempt;
    private int maxAttempts;

    /**
     * @param client
     */
    public ReconnectAttemptFailed(Client client, Exception e, int attempt, int maxAttempts)
    {
        super(client, e);
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
    }

    public int getAttempt()
    {
        return attempt;
    }

    public void setAttempt(int attempt)
    {
        this.attempt = attempt;
    }

    public int getMaxAttempts()
    {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts)
    {
        this.maxAttempts = maxAttempts;
    }
}