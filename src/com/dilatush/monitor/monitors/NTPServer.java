package com.dilatush.monitor.monitors;

import com.dilatush.monitor.Config;
import com.dilatush.mop.Mailbox;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.Files;
import com.dilatush.util.Outcome;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.Strings.isEmpty;
import static java.lang.Thread.sleep;


/**
 * Instances of this class monitor a TF-1006-PRO NTP server.  This server uses GPS clock references and a disciplined oscillator to implement a Stratum 1
 * NTP server.  The monitor produces MOP events for significant status changes, statistics to be recorded in the database, and status reports for the website.
 */
public class NTPServer extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private final String urlStr;
    private final String basicAuthentication;


    /**
     * Create a new instance of this class to monitor a TF-1006-PRO NTP server at the given URL, with the given username and password.
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


    /**
     * Perform the periodic monitoring.  For this monitor that interval should be 60 seconds.
     */
    @Override
    public void run() {

        try {

            // scrape the current status data from the TF-1006-PRO NTP server...
            var scraping = scrape();

            // based on the scraped data, send status, statistics, and any events...
            sendStatus( scraping );
            sendStatistics( scraping );
            sendEvents( scraping );
        }

        // if we get any exceptions, then we log them and send a rate-limited event...
        catch( Exception _e ) {

            LOGGER.log( Level.SEVERE, "Problem reading from NTP Server", _e );

            var subject = _e.getClass().getSimpleName();
            var message = _e.getMessage();
            sendEvent( Duration.ofHours( 1 ), "NTPServer.readFailure", "NTPServer.readFailure", subject, message, 7 );
        }
    }


    private void sendStatus( final Scraping _scraping ) {

    }


    private void sendStatistics( final Scraping _scraping ) {

    }


    private void sendEvents( final Scraping _scraping ) {

    }


    /**
     * Queries the TF-1006-PRO NTP server for its current status, and returns that data in a ready-to-use form.  See comments at the end of the source file for an example of
     * the raw scraped data.
     *
     * @return The data scraped from the TF-1006-PRO NTP server, munged into a ready-to-use form.
     * @throws IOException On any problem reading the data from the TF-1006-PRO NTP server.
     * @throws ParserConfigurationException On any problem extracting particular information from the scraped data.
     * @throws SAXException On any problem extracting particular information from the scraped data.
     * @throws XPathExpressionException On any problem extracting particular information from the scraped data.
     */
    private Scraping scrape() throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {

        // get an XML string with all the data scraped from the NTP server...
        String xml =
                "<ntp>" +
                scrape( "time"  ) +
                scrape( "state" ) +
                scrape( "gnss"  ) +
                "</ntp>";

        // get ready to use XPath to pull data out of the XML string...
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document doc = builder.parse( new InputSource( new StringReader( xml ) ) );
        var result = new Scraping();
        XPath xPath = XPathFactory.newInstance().newXPath();

        // extract the data we need...
        result.uptime      = extractUptime( doc, xPath );
        result.referenceUp = "Locked".equals( xPath.compile( "//ntp/state/loppstate" ).evaluate( doc, XPathConstants.STRING ) );
        result.ntpUp       = "ACTIVE".equals( xPath.compile( "//ntp/state/ntpstate" ).evaluate( doc, XPathConstants.STRING ) );
        result.tie         = extractInt( doc, xPath, "//ntp/state/tie" );
        result.antennaOK   = "OK".equals( xPath.compile( "//ntp/gnss/ant" ).evaluate( doc, XPathConstants.STRING ) );
        result.satsUsed    = extractInt( doc, xPath, "//ntp/gnss/svused" );
        result.satsTotal   = extractSatellitesVisible( doc, xPath );
        result.lat         = extractLatLon( doc, xPath, "//ntp/gnss/lat" );
        result.lon         = extractLatLon( doc, xPath, "//ntp/gnss/long" );
        result.altitude    = extractAltitude( doc, xPath );

        return result;
    }


    /**
     * Extract the altitude in feet from the given XML document.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @return The altitude of the TF-1006-PRO NTP server in feet.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private float extractAltitude( final Document _doc, final XPath _xPath ) throws XPathExpressionException {
        var fld = (String)_xPath.compile( "//ntp/gnss/alt" ).evaluate( _doc, XPathConstants.STRING );  // will be something like "1433.2 m"...
        var parts = fld.split( " " );
        return 3.28084F * Float.parseFloat( parts[0] );  // convert meters to feet...
    }


    /**
     * Extract the latitude or longitude in degrees from the given XML document and path.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @param _path The path to the data to extract.
     * @return The latitude or longitude of the TF-1006-PRO NTP server in degrees.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private float extractLatLon( final Document _doc, final XPath _xPath, final String _path ) throws XPathExpressionException {
        var fld = (String)_xPath.compile( _path ).evaluate( _doc, XPathConstants.STRING );  // will be something like "W 11154.832", where 111 is degrees and everything else minutes...
        var parts = fld.split( " " );
        var negate = "S".equals( parts[0] ) || "W".equals( parts[0] );  // south latitude and west longitude are negative...
        var latlon = Double.parseDouble( parts[1] );
        var intDeg = Math.floor( latlon / 100D );
        var frcDeg = (latlon / 100D - intDeg) * 100D / 60D;
        var deg = intDeg + frcDeg;
        return (float)(negate ? -deg : deg);
    }


    /** Extract the total satellites visible by summing the GPS, Galileo, and GLOSNASS satellites visible.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @return The total satellites visible.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private int extractSatellitesVisible( final Document _doc, final XPath _xPath ) throws XPathExpressionException {
        return
                extractSatellitesVisible( _doc, _xPath, "//ntp/gnss/gpsinfo" ) +  // GPS info...
                extractSatellitesVisible( _doc, _xPath, "//ntp/gnss/glinfo" ) +   // GLOSNASS info...
                extractSatellitesVisible( _doc, _xPath, "//ntp/gnss/gainfo" );    // Galileo info...
    }


    /**
     * Extract the satellites visible in the constellation at the given path.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @param _path The path to the data to extract.
     * @return The satellites visible in the constellation at the given path.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private int extractSatellitesVisible( final Document _doc, final XPath _xPath, final String _path ) throws XPathExpressionException {
        var fld = (String)_xPath.compile( _path ).evaluate( _doc, XPathConstants.STRING );  // will be something like "4/6", 4 used, 6 visible...
        var parts = fld.split( "/" );
        return Integer.parseInt( parts[1] );
    }


    /**
     * Extract an integer from the given path in the given document.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @param _path The path to the data to extract.
     * @return The extracted integer.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private int extractInt( final Document _doc, final XPath _xPath, final String _path ) throws XPathExpressionException {
        return (int)Math.round( (double)_xPath.compile( _path ).evaluate( _doc, XPathConstants.NUMBER ) );
    }


    /**
     * Extract the uptime in hours from the given document.
     *
     * @param _doc The XML document scraped from the TF-1006-PRO NTP server.
     * @param _xPath The XPath to use.
     * @return The uptime in hours.
     * @throws XPathExpressionException On any problem parsing the XPath.
     */
    private int extractUptime( final Document _doc, final XPath _xPath ) throws XPathExpressionException {
        var uptime = (String) _xPath.compile( "//ntp/time/rtime" ).evaluate( _doc, XPathConstants.STRING );  // will be something like "110 Day 12:03:22"...
        var parts = uptime.split( " Day " );
        var time = parts[1].split( ":" );
        return Integer.parseInt( parts[0] ) * 24 + Integer.parseInt( time[0] );
    }


    /**
     * Return the data scraped from the TF-1006-PRO NTP server at the given page.  The data will be XML; see comment at the end of the source code file for an example.  The valid
     * pages are "time", "state", and "gnss".
     *
     * @param _page The page to scrape; must be one of "time", "state", or "gnss".
     * @return The scraped XML data.
     * @throws IOException On any problem reading the data.
     */
    private String scrape( final String _page ) throws IOException {

        // synthesize the right URL for the desired page, like "http://ntpserver.dilatush.com/xml/gnss.xml"...
        var url = new URL( urlStr + "/xml/" + _page + ".xml" );

        // set up our connection and request...
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod( "GET" );
        con.setRequestProperty("Content-Type", "text/xml");
        con.setRequestProperty( "Authorization", "Basic " + basicAuthentication );
        con.setInstanceFollowRedirects(false);

        // make the request...
        int status = con.getResponseCode();

        // if we get anything other than an OK (200), error out...
        if( status != 200 ) throw new IOException( "HTTP Request status was not ok (200): " + status );

        // read the response...
        BufferedReader in = new BufferedReader( new InputStreamReader( con.getInputStream() ) );
        String inputLine;
        StringBuilder content = new StringBuilder();
        while( (inputLine = in.readLine()) != null ) {
            content.append( inputLine );
        }
        in.close();

        // cleanup...
        con.disconnect();

        return content.toString();
    }


    /**
     * Data structure to hold the scraped and processed data from the TF-1006-PRO NTP server.
     */
    private static class Scraping {
        private int     uptime;      // how long the NTP server has been up, in hours...
        private boolean referenceUp; // true if the frequency reference is up (GPS locked and disciplined oscillator locked)...
        private boolean ntpUp;       // true if the NTP server software is up...
        private int     tie;         // time interval error (phase error over an interval) in nanoseconds...
        private int     satsUsed;    // number of satellites used for GPS solution...
        private int     satsTotal;   // number of satellites visible to the NTP server's receiver...
        private float   lat;         // latitude in degrees (+ for north, - for south)...
        private float   lon;         // longitude in degrees (+ for east, - for west)...
        private float   altitude;    // altitude in feet...
        private boolean antennaOK;   // true if the antenna is ok...
    }


    /**
     * This stub main is here for troubleshooting only - using it you can run the monitor just once, from a development machine.
     */
    public static void main( final String[] _args ) throws InterruptedException {

        Config config = new Config();
        Outcome<?> result = config.init( "MonitorConfigurator", "configuration.java", Files.readToString( new File( "credentials.txt" ) ) );
        if( result.notOk() ) throw new IllegalArgumentException( "bad configuration" );
        PostOffice po = new PostOffice( config.postOfficeConfig );
        Mailbox mailbox = po.createMailbox( "monitor" );

        var mon = new NTPServer( mailbox, config.ntpServerURL, config.ntpServerUsername, config.ntpServerPassword );
        mon.run();

        sleep( 5000 );

        //noinspection ResultOfMethodCallIgnored
        config.hashCode();
    }
}


/*
   Sample synthesized scraped XML blob:

   <ntp>
      <time>
         <rtime>106 Day 17:34:26</rtime>
         <ctime>2023/04/17 15:41:08</ctime>
         <ltime>2023/04/17 08:41:08</ltime>
         <temp>43</temp>
         <holdtime>0 Day 00:00:00</holdtime>
      </time>
      <state>
         <syncsrc>GPS+GLONASS+Galileo</syncsrc>
         <loppstate>Locked</loppstate>
         <tie>-40</tie>
         <control>31446</control>
         <ntpstate>ACTIVE</ntpstate>
         <color>green</color>
      </state>
      <gnss>
         <ant>OK</ant>
         <const>GPS+GLONASS+Galileo</const>
         <svused>20</svused>
         <gpsinfo>9/10</gpsinfo>
         <bdinfo>0/0</bdinfo>
         <glinfo>7/9</glinfo>
         <gainfo>4/9</gainfo>
         <lat>N 4134.9827</lat>
         <long>W 11150.3990</long>
         <alt>1471.9 m</alt>
         </gnss>
   </ntp>

 */