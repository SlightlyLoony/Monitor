package com.dilatush.monitor.monitors;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;

public class NTPServer {

    public static void main( final String[] _args ) throws IOException {


        /*
         * time.xml
         * state.xml
         * gnss.xml
         * clients.xml
         * network0.xml
         */
        URL url = new URL("http://admin:zQGNzRLwE_7F8Jxi@ntpserver.dilatush.com/xml/gnss.xml");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");

        con.setRequestProperty("Content-Type", "text/xml");
        con.setRequestProperty( "Authorization", "Basic YWRtaW46elFHTnpSTHdFXzdGOEp4aQ==" );

        con.setInstanceFollowRedirects(false);

        int status = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();

        con.disconnect();

        con.hashCode();
    }
}
