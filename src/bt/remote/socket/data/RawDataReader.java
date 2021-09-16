package bt.remote.socket.data;

import java.io.DataInputStream;

@FunctionalInterface
public interface RawDataReader
{
    public byte[] read(DataInputStream in);
}