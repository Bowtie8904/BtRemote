package bt.remote.web;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

import bt.log.Logger;

/**
 * @author &#8904
 *
 */
public class WebUtils
{
    public static void downloadImage(String search, String path) throws IOException
    {
        InputStream inputStream = null;
        OutputStream outputStream = null;

        try
        {
            URL url = new URL(search);
            String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36";
            URLConnection con = url.openConnection();

            con.setRequestProperty("User-Agent",
                                   USER_AGENT);
            inputStream = con.getInputStream();
            outputStream = new FileOutputStream(path);

            byte[] buffer = new byte[2048];
            int length;

            while ((length = inputStream.read(buffer)) != -1)
            {
                outputStream.write(buffer,
                                   0,
                                   length);
            }
        }
        catch (Exception ex)
        {
            Logger.global().print(ex);
        }

        outputStream.close();
        inputStream.close();
    }
}
