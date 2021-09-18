package bt.remote.socket.evnt;

import bt.remote.socket.Client;

public class ReconnectAttempt
{
    private Client client;
    private int attempt;
    private int maxAttempts;

    /**
     * @param client
     */
    public ReconnectAttempt(Client client, int attempt, int maxAttempts)
    {
        this.client = client;
        this.attempt = attempt;
        this.maxAttempts = maxAttempts;
    }

    /**
     * @return the client
     */
    public Client getClient()
    {
        return this.client;
    }

    /**
     * @param client
     *            the client to set
     */
    public void setClient(Client client)
    {
        this.client = client;
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