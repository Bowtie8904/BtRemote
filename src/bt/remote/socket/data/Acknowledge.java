package bt.remote.socket.data;

import bt.async.Data;

/**
 * @author &#8904
 *
 */
public class Acknowledge<T> extends Message<T>
{
    /**
     * @param data
     */
    public Acknowledge(Data<T> data)
    {
        super(data);
    }
}