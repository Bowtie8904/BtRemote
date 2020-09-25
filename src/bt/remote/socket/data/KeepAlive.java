package bt.remote.socket.data;

import bt.async.Data;

/**
 * @author &#8904
 *
 */
public class KeepAlive<T> extends Message<T>
{
    /**
     * @param data
     */
    public KeepAlive(Data<T> data)
    {
        super(data);
    }
}