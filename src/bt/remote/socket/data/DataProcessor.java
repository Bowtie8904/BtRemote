package bt.remote.socket.data;

import bt.async.Data;

/**
 * @author &#8904
 *
 */
@FunctionalInterface
public interface DataProcessor
{
    /**
     * Expected to handle the incoming data and produce a fitting response (or null if no response is required).
     *
     * @param incoming
     * @return A response to the incoming data or null if no response is required.
     */
    public Object process(Data incoming);
}