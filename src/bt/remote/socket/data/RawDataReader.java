package bt.remote.socket.data;

import java.io.DataInputStream;
import java.io.IOException;

@FunctionalInterface
public interface RawDataReader
{
    public byte[] read(DataInputStream in) throws IOException;
}