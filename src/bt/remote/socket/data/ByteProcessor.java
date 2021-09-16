package bt.remote.socket.data;

import bt.async.Data;

@FunctionalInterface
public interface ByteProcessor
{
    /**
     * Expected to handle the incoming data and produce a fitting response (or null if no response is required).
     *
     * @param incoming
     * @return A response to the incoming data or null if no response is required.
     */
    public byte[] process(byte[] incoming);
}