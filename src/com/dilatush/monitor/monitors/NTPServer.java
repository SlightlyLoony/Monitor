package com.dilatush.monitor.monitors;

import com.dilatush.monitor.Config;
import com.dilatush.mop.Mailbox;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.Files;
import com.dilatush.util.Outcome;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.Strings.isEmpty;
import static java.lang.Thread.sleep;


/**
 * Instances of this class monitor a TF-1006-PRO NTP server.  This server uses GPS clock references and a disciplined oscillator to implement a Stratum 1
 * NTP server.  The monitor produces MOP events for significant status changes, and status reports for the website.
 */
public class NTPServer extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private final String urlStr;
    private final String basicAuthentication;


    /**
     * Create a new instance of this class to monitor a TF-1006-PRO NTP server at the given URL, with the given user name and password.
     *
     * @param _mailbox The mailbox for this monitor to use.
     * @param _url The URL of the TF-1006-PRO NTP server.
     * @param _user The username for the TF-1006-PRO NTP server.
     * @param _password The password for the given user on the TF-1006-PRO NTP server.
     */
    public NTPServer( final Mailbox _mailbox, final String _url, final String _user, final String _password ) {
        super( _mailbox );

        // sanity checks...
        if( isEmpty( _url )      ) throw new IllegalArgumentException( "_url must be provided" );
        if( isEmpty( _user )     ) throw new IllegalArgumentException( "_user must be provided" );
        if( isEmpty( _password ) ) throw new IllegalArgumentException( "_password must be provided" );

        urlStr = _url;
        basicAuthentication = getBasicAuthentication( _user, _password );
    }


    private static final Pattern UPTIME_PATTERN = Pattern.compile( ".*<rtime>([0-9]*) Day ([0-9]*).*" );
    private static final Pattern STATE_PATTERN  = Pattern.compile( ".*<loppstate>(.*?)</loppstate>.*?<tie>(.*?)</tie>.*?<ntpstate>(.*?)</ntpstate>.*" );
    private static final Pattern GNSS_PATTERN   = Pattern.compile(
            ".*?<ant>(.*?)</ant>.*?<svused>([0-9]*?)</svused>.*?<gpsinfo>[0-9]*?/([0-9]*?)</gpsinfo>.*?<glinfo>[0-9]*?/([0-9]*?)</glinfo>.*?<gainfo>" +
            "[0-9]*?/([0-9]*?)</gainfo>.*?<lat>([NS]) ([0-9.]*?)</lat>.*?<long>([EW]) ([0-9.]*?)</long>.*?<alt>([0-9.]*?) m</alt>.*"
    );

    /**
     * Perform the periodic monitoring.  For this monitor that interval should be 60 seconds.
     */
    @Override
    public void run() {

        try {

            // first we scrape the API for status information...
            var time  = scrape( "time"  );
            var state = scrape( "state" );
            var gnss  = scrape( "gnss"  );

            // extract the useful and interesting information from what we scraped...
            var uptime      = 0;          // how long the NTP server has been up, in hours...
            var referenceUp = false;      // true if the frequency reference is up (GPS locked and disciplined oscillator locked)...
            var ntpUp       = false;      // true if the NTP server software is up...
            var tie         = 0;          // time interval error (phase error over an interval) in nanoseconds...
            var satsUsed    = 0;          // number of satellites used for GPS solution...
            var satsTotal   = 0;          // number of satellites visible to the NTP server's receiver...
            var gpsTotal    = 0;          // number of GPS satellites visible to the NTP server's receiver...
            var galTotal    = 0;          // number of Galileo satellites visible to the NTP server's receiver...
            var gloTotal    = 0;          // number of GLOSNASS satellites visible to the NTP server's receiver...
            var lat         = 0F;         // latitude in degrees (+ for north, - for south)...
            var lon         = 0F;         // longitude in degrees (+ for east, - for west)...
            var altitude    = 0F;         // altitude in feet...
            var antennaOK   = false;      // true if the antenna is ok...

            // keep track of whether we have valid data or not...
            var valid = true;

            // first the uptime in hours...
            var mat = UPTIME_PATTERN.matcher( time );
            if( mat.matches() )
                uptime = Integer.parseInt( mat.group( 1 ) ) * 24 + Integer.parseInt( mat.group( 2 ) );
            else
                valid = false;

            // then the loop state, time interval error (TIE), and NTP state...
            mat = STATE_PATTERN.matcher( state );
            if( mat.matches() ) {
                referenceUp = "Locked".equals( mat.group( 1 ) );
                ntpUp = "ACTIVE".equals( mat.group( 3 ) );
                tie = Integer.parseInt( mat.group( 2 ) );
            }
            else
                valid = false;

            // and finally the satellite information...
            mat = GNSS_PATTERN.matcher( gnss );
            if( mat.matches() ) {
                antennaOK = "OK".equals( mat.group( 1 ) );
                satsUsed = Integer.parseInt( mat.group( 2 ) );
                gpsTotal = Integer.parseInt( mat.group( 3 ) );
                gloTotal = Integer.parseInt( mat.group( 4 ) );
                galTotal = Integer.parseInt( mat.group( 5 ) );
                satsTotal = gpsTotal + gloTotal + galTotal;
                lat = convertLocation( mat.group( 7 ), "S".equals( mat.group( 6 ) ) );
                lon = convertLocation( mat.group( 9 ), "W".equals( mat.group( 8 ) ) );
                altitude = Float.parseFloat( mat.group( 10 ) ) * 3.28084F;
            }
            else
                valid = false;

            // if we don't have a valid value, then our data didn't match one of the patterns...
            if( !valid ) {

            }

            // when we get here, we've scraped the NTP server for status information, and converted the raw scrape to usable values...


            this.hashCode();
        }

        // if we get any exceptions, then we log them and send a rate-limited event...
        catch( Exception _e ) {

            LOGGER.log( Level.SEVERE, "Problem reading from NTP Server", _e );

            var subject = _e.getClass().getSimpleName();
            var message = _e.getMessage();
            sendEvent( Duration.ofHours( 1 ), "NTPServer.readFailure", "NTPServer.readFailure", subject, message, 7 );
        }
    }

    // <time><rtime>106 Day 17:34:26</rtime><ctime>2023/04/17 15:41:08</ctime><ltime>2023/04/17 08:41:08</ltime><temp>43</temp><holdtime>0 Day 00:00:00</holdtime></time>

    // <time>
    //    <rtime>106 Day 17:34:26</rtime>
    //    <ctime>2023/04/17 15:41:08</ctime>
    //    <ltime>2023/04/17 08:41:08</ltime>
    //    <temp>43</temp>
    //    <holdtime>0 Day 00:00:00</holdtime>
    // </time>

    // <state><syncsrc>GPS+GLONASS+Galileo</syncsrc><loppstate>Locked</loppstate><tie>-40</tie><control>31446</control><ntpstate>ACTIVE</ntpstate><color>green</color></state>

    // <state>
    //    <syncsrc>GPS+GLONASS+Galileo</syncsrc>
    //    <loppstate>Locked</loppstate>
    //    <tie>-40</tie>
    //    <control>31446</control>
    //    <ntpstate>ACTIVE</ntpstate>
    //    <color>green</color>
    // </state>

    // <gnss><ant>OK</ant><const>GPS+GLONASS+Galileo</const><svused>20</svused><gpsinfo>9/10</gpsinfo><bdinfo>0/0</bdinfo><glinfo>7/9</glinfo><gainfo>4/9</gainfo><lat>N 4134.9827</lat><long>W 11150.3990</long><alt>1471.9 m</alt></gnss>

    // <gnss>
    //    <ant>OK</ant>
    //    <const>GPS+GLONASS+Galileo</const>
    //    <svused>20</svused>
    //    <gpsinfo>9/10</gpsinfo>
    //    <bdinfo>0/0</bdinfo>
    //    <glinfo>7/9</glinfo>
    //    <gainfo>4/9</gainfo>
    //    <lat>N 4134.9827</lat>
    //    <long>W 11150.3990</long>
    //    <alt>1471.9 m</alt>
    // </gnss>


    private float convertLocation( final String _latlon, final boolean _negate ) {
        var latlon = Double.parseDouble( _latlon );
        var intDeg = Math.floor( latlon / 100D );
        var frcDeg = (latlon / 100D - intDeg) * 100D / 60D;
        var deg = intDeg + frcDeg;
        return (float)(_negate ? -deg : deg);
    }



    private String scrape( final String _page ) throws IOException {

        var url = new URL( urlStr + "/xml/" + _page + ".xml" );
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod( "GET" );

        con.setRequestProperty("Content-Type", "text/xml");
        con.setRequestProperty( "Authorization", "Basic " + basicAuthentication );

        con.setInstanceFollowRedirects(false);

        int status = con.getResponseCode();

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuilder content = new StringBuilder();
        while( (inputLine = in.readLine()) != null ) {
            content.append( inputLine );
        }
        in.close();

        con.disconnect();

        return content.toString();
    }


    /**
     * Return the base-64 encoded basic authentication string for the given username and password.
     *
     * @param _user The username to encode.
     * @param _password The password to encode.
     * @return The base-64 encoded basic authentication string.
     */
    private String getBasicAuthentication( final String _user, final String _password ) {
        var up = _user + ":" + _password;
        var b64e = Base64.getEncoder();
        return b64e.encodeToString( up.getBytes( StandardCharsets.UTF_8) );
    }


    /**
     * This stub main is here for troubleshooting only - using it you can run the monitor just once, from a development machine.
     */
    public static void main( final String[] _args ) throws IOException, InterruptedException {

        Config config = new Config();
        Outcome<?> result = config.init( "MonitorConfigurator", "configuration.java", Files.readToString( new File( "credentials.txt" ) ) );
        PostOffice po = new PostOffice( config.postOfficeConfig );
        Mailbox mailbox = po.createMailbox( "monitor" );

        var mon = new NTPServer( mailbox, "http://ntpserver.dilatush.com", "admin", "zQGNzRLwE_7F8Jxi" );
        mon.run();

        sleep( 5000 );

        //noinspection ResultOfMethodCallIgnored
        config.hashCode();
    }
}
