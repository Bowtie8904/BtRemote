package bt.remote.socket.data;

import bt.async.Data;

/**
 * @author &#8904
 * @param <T>
 *
 */
public class Response<T> extends Message<T>
{
    /**
     * @param data
     */
    public Response(Data<T> data)
    {
        super(data);
    }
}