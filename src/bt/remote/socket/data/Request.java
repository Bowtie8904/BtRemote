package bt.remote.socket.data;

import bt.async.Data;

/**
 * @author &#8904
 *
 */
public class Request<T> extends Message<T>
{
    /**
     * @param data
     */
    public Request(Data<T> data)
    {
        super(data);
    }
}