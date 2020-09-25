package bt.remote.socket.data;

import java.io.Serializable;

import bt.async.Data;

/**
 * @author &#8904
 *
 */
public class Message<T> implements Serializable
{
    protected Data<T> data;

    public Message(Data<T> data)
    {
        this.data = data;
    }

    /**
     * @return the data
     */
    public Data<T> getData()
    {
        return this.data;
    }

    /**
     * @param data
     *            the data to set
     */
    public void setData(Data<T> data)
    {
        this.data = data;
    }
}