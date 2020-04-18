package bt.remote.rest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import org.json.JSONObject;

import bt.utils.json.JSON;
import bt.utils.log.Logger;

/**
 * @author &#8904
 *
 */
public final class REST
{
    /**
     * Performs a POST request to the given endpoint and transmits the given JSON.
     *
     * @param action
     *            The action that should be performed. The value will be added to the json with the key 'Task'.
     * @param endpoint
     *            The endpoint for the request.
     * @param json
     *            The JSON that should be sent to the endpoint.
     * @return The JSON response from the endpoint.
     */
    public static synchronized JSONObject POST(String endpoint, Map<String, String> headers, JSONObject json)
    {
        JSONObject returnJson = null;

        try
        {
            String url = endpoint;

            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection)obj.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type",
                                   "application/json; charset=UTF-8");
            con.setRequestProperty("User-Agent",
                                   "Mozilla/5.0");
            con.setRequestProperty("Accept",
                                   "application/json");

            if (headers != null)
            {
                for (String key : headers.keySet())
                {
                    con.setRequestProperty(key,
                                           headers.get(key));
                }
            }

            try (OutputStream os = con.getOutputStream())
            {
                os.write(json.toString().getBytes("UTF-8"));
            }

            StringBuffer response = null;

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream())))
            {

                String inputLine;
                response = new StringBuffer();

                while ((inputLine = in.readLine()) != null)
                {
                    response.append(inputLine);
                }
            }

            returnJson = JSON.parse(response != null ? response.toString() : null);
        }
        catch (Exception e)
        {
            Logger.global().print(e);
        }

        return returnJson;
    }

    public static synchronized JSONObject POST(String endpoint, Map<String, String> headers, String... params)
    {
        JSONObject returnJson = null;

        try
        {
            String url = endpoint;

            String urlParams = "";

            for (String param : params)
            {
                urlParams += param;
            }

            if (urlParams.endsWith("&"))
            {
                urlParams = urlParams.substring(0,
                                                urlParams.length() - 1);
            }

            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection)obj.openConnection();
            con.setDoOutput(true);
            con.setDoInput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type",
                                   "application/x-www-form-urlencoded");
            con.setRequestProperty("User-Agent",
                                   "Mozilla/5.0");
            con.setRequestProperty("Accept",
                                   "application/json");

            if (headers != null)
            {
                for (String key : headers.keySet())
                {
                    con.setRequestProperty(key,
                                           headers.get(key));
                }
            }

            try (OutputStream os = con.getOutputStream())
            {
                os.write(urlParams.getBytes("UTF-8"));
            }

            StringBuffer response = null;

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream())))
            {

                String inputLine;
                response = new StringBuffer();

                while ((inputLine = in.readLine()) != null)
                {
                    response.append(inputLine);
                }
            }

            returnJson = JSON.parse(response != null ? response.toString() : null);
        }
        catch (Exception e)
        {
            Logger.global().print(e);
        }

        return returnJson;
    }

    /**
     * Performs a GET request to the given endpoint with the given parameters.
     *
     * <p>
     * Use {@link #formParam(String, String)} to correctly format parameter pairs of name and value.
     * </p>
     *
     * @param action
     *            The action that should be performed. The value will be added as the parameter 'task'
     * @param endpoint
     *            The endpoint for the request.
     * @param params
     *            URL parameters for the endpoint. Format: ?key=value&
     * @return The JSON response from the endpoint.
     */
    public static synchronized JSONObject GET(String endpoint, Map<String, String> headers, String... params)
    {
        JSONObject json = null;

        try
        {
            String url = endpoint;

            if (params.length != 0)
            {
                url += "?";
            }

            for (String param : params)
            {
                url += param;
            }

            if (url.endsWith("&"))
            {
                url = url.substring(0,
                                    url.length() - 1);
            }

            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection)obj.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent",
                                   "Mozilla/5.0");
            con.setRequestProperty("Accept",
                                   "application/json");

            if (headers != null)
            {
                for (String key : headers.keySet())
                {
                    con.setRequestProperty(key,
                                           headers.get(key));
                }
            }

            StringBuffer response = null;

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream())))
            {

                String inputLine;
                response = new StringBuffer();

                while ((inputLine = in.readLine()) != null)
                {
                    response.append(inputLine);
                }
            }
            catch (Exception e)
            {}

            json = JSON.parse(response != null ? response.toString() : null);
        }
        catch (Exception e)
        {
            Logger.global().print(e);
        }

        return json;
    }

    /**
     * Formats the given Strings in a corect url parameter format.
     *
     * @param key
     * @param value
     * @return The formatted String
     *
     *         <pre>
     * key=value&
     *         </pre>
     */
    public static String formParam(String key, String value)
    {
        StringBuilder result = new StringBuilder();

        try
        {
            result.append(URLEncoder.encode(key,
                                            "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(value,
                                            "UTF-8"));
            result.append("&");
        }
        catch (UnsupportedEncodingException e)
        {
            Logger.global().print(e);
        }

        return result.toString();
    }
}
