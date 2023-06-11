package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;
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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dilatush.monitor.monitors.AMonitor.TriState.*;
import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.General.isNull;


/**
 * Instances of this class monitor a TF-1006-PRO NTP server.  This server uses GPS clock references and a disciplined oscillator to implement a Stratum 1
 * NTP server.  The monitor produces MOP events for significant status changes, statistics to be recorded in the database, and status reports for the website.
 */
public class NTPServer extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private static final int TIE_MAX_ERROR_NS = 100;  // maximum allowable Time Interval Error (TIE), in nanoseconds...
    private static final int MIN_SATS_USED    = 6;
    private static final int MIN_SATS_VISIBLE = 10;

    private final String urlStr;
    private final String basicAuthentication;

    // keep track of the last state of variables that we send events for...
    private TriState lastReferenceUp   = UNKNOWN;
    private TriState lastNTPUp         = UNKNOWN;
    private TriState lastTIEOK         = UNKNOWN;
    private TriState lastSatsUsedOK    = UNKNOWN;
    private TriState lastSatsVisibleOK = UNKNOWN;
    private TriState lastAntennaOK     = UNKNOWN;


    /**
     * Create a new instance of this class to monitor a TF-1006-PRO NTP server at the given URL, with the given username and password (contained in the parameters).
     *
     * @param _mailbox The mailbox for this monitor to use.
     * @param _params The map of parameters, which must include "URL", "username", and "password".
     * @param _interval the interval between runs for this monitor.
     */
    public NTPServer( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );

        if( isNull( _params ) ) throw new IllegalArgumentException( "_params must be supplied" );

        var url      = (String) _params.get( "URL"      );
        var username = (String) _params.get( "username" );
        var password = (String) _params.get( "password" );

        if( isNull( url, username, password ) ) throw new IllegalArgumentException( "URL, username, and password parameters must all be supplied" );

        urlStr = url;
        basicAuthentication = getBasicAuthentication( username, password );
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


    /**
     * Send a published NTP monitoring message.
     *
     * @param _scraping The data scraped from the TF-1006-PRO NTP server.
     */
    private void sendStatus( final Scraping _scraping ) {

        Message msg = mailbox.createPublishMessage( "ntp.monitor" );

        // send the message interval...
        msg.putDotted( "monitor.ntp.messageIntervalMs",     interval.toMillis()        );

        // fill in our collected data...
        msg.putDotted( "monitor.ntp.uptimeHours",           _scraping.uptime           );
        msg.putDotted( "monitor.ntp.tieNs",                 _scraping.tie              );
        msg.putDotted( "monitor.ntp.referenceUp",           _scraping.referenceUp      );
        msg.putDotted( "monitor.ntp.ntpUp",                 _scraping.ntpUp            );
        msg.putDotted( "monitor.ntp.satsUsed",              _scraping.satsUsed         );
        msg.putDotted( "monitor.ntp.satsVisible",           _scraping.satsTotal        );
        msg.putDotted( "monitor.ntp.antennaOk",             _scraping.antennaOK        );

        // send it!
        mailbox.send( msg );
        LOGGER.info( "Sent NTP server monitor message" );
    }


    /**
     * Send event with current NTP statistics, for insertion in the NTP statistics database...
     *
     * @param _scraping The data scraped from the TF-1006-PRO NTP server.
     */
    private void sendStatistics( final Scraping _scraping ) {

        // build our event message...
        Message msg = mailbox.createDirectMessage( "events.post", "event.post", false );
        msg.putDotted( "tag",                          "ntpstats"                 );
        msg.putDotted( "timestamp",                    System.currentTimeMillis() );
        msg.putDotted( "fields.uptimeHours",           _scraping.uptime           );
        msg.putDotted( "fields.tieNs",                 _scraping.tie              );
        msg.putDotted( "fields.referenceUp",           _scraping.referenceUp      );
        msg.putDotted( "fields.ntpUp",                 _scraping.ntpUp            );
        msg.putDotted( "fields.satsUsed",              _scraping.satsUsed         );
        msg.putDotted( "fields.satsVisible",           _scraping.satsTotal        );
        msg.putDotted( "fields.antennaOk",             _scraping.antennaOK        );
        msg.putDotted( "fields.latitude",              _scraping.lat              );
        msg.putDotted( "fields.longitude",             _scraping.lon              );
        msg.putDotted( "fields.altitudeFeet",          _scraping.altitude         );

        // send it!
        mailbox.send( msg );
        LOGGER.info( "Sent NTP server statistics message" );
    }


    /**
     * Send events when any monitored conditions change.
     *
     * @param _scraping The data scraped from the TF-1006-PRO NTP server.
     */
    private void sendEvents( final Scraping _scraping ) {

        // handle change in NTP GPS reference up or down...
        handleChangedCondition( () -> _scraping.referenceUp, () -> lastReferenceUp, (t) -> lastReferenceUp = t,
                "NTPServer.referenceDown",     "NTP GPS reference is down",      "NTP GPS reference is down",                            7,
                "NTPServer.referenceUp",       "NTP GPS reference is up",        "NTP GPS reference is up",                              2,
                "NTPServer.referenceWentDown", "NTP GPS reference went down",    "NTP GPS reference went down after being up",           7,
                "NTPServer.referenceWentUp",   "NTP GPS reference came back up", "NTP GPS reference came up after being down",           7 );

        // handle change in NTP server application up or down...
        handleChangedCondition( () -> _scraping.ntpUp, () -> lastNTPUp, (t) -> lastNTPUp = t,
                "NTPServer.ntpDown",     "NTP server application is down",      "NTP server application is down",                            7,
                "NTPServer.ntpUp",       "NTP server application is up",        "NTP server application is up",                              2,
                "NTPServer.ntpWentDown", "NTP server application went down",    "NTP server application went down after being up",           7,
                "NTPServer.ntpWentUp",   "NTP server application came back up", "NTP server application came up after being down",           7 );

        // handle change in time interval error (TIE) in-bounds or out-of-bounds...
        handleChangedCondition( () -> Math.abs( _scraping.tie ) <= TIE_MAX_ERROR_NS, () -> lastTIEOK, (t) -> lastTIEOK = t,
                "NTPServer.tieOOB",      "NTP reference TIE is out-of-bounds",    "NTP reference Time Interval Error (TIE) is out-of-bounds: "   + _scraping.tie + "ns", 6,
                "NTPServer.tieIB",       "NTP reference TIE is in-bounds",        "NTP reference Time Interval Error (TIE) is in-bounds: "       + _scraping.tie + "ns", 5,
                "NTPServer.tieWentOOB",  "NTP reference TIE went out-of-bounds",  "NTP reference Time Interval Error (TIE) went out-of-bounds: " + _scraping.tie + "ns", 6,
                "NTPServer.tieWentIB",   "NTP reference TIE came back in-bounds", "NTP reference Time Interval Error (TIE) went in-bounds: "     + _scraping.tie + "ns", 6 );

        // handle change in number of satellites used for GPS fix being enough or not enough...
        handleChangedCondition( () -> Math.abs( _scraping.satsUsed ) >= MIN_SATS_USED, () -> lastSatsUsedOK, (t) -> lastSatsUsedOK = t,
                "NTPServer.satsUsedOOB",      "NTP GPS not enough satellites used",       "NTP GPS not enough satellites used: "       + _scraping.satsUsed, 6,
                "NTPServer.satsUsedIB",       "NTP GPS enough satellites used",           "NTP GPS enough satellites used: "           + _scraping.satsUsed, 5,
                "NTPServer.satsUsedWentOOB",  "NTP GPS no longer enough satellites used", "NTP GPS no longer enough satellites used: " + _scraping.satsUsed, 6,
                "NTPServer.satsUsedWentIB",   "NTP GPS now enough satellites used",       "NTP GPS now enough satellites used: "       + _scraping.satsUsed, 6 );

        // handle change in number of satellites visible for GPS fix being enough or not enough...
        handleChangedCondition( () -> Math.abs( _scraping.satsTotal ) >= MIN_SATS_VISIBLE, () -> lastSatsVisibleOK, (t) -> lastSatsVisibleOK = t,
                "NTPServer.satsVisibleOOB",      "NTP GPS not enough satellites visible",       "NTP GPS not enough satellites visible: "       + _scraping.satsTotal, 6,
                "NTPServer.satsVisibleIB",       "NTP GPS enough satellites visible",           "NTP GPS enough satellites visible: "           + _scraping.satsTotal, 5,
                "NTPServer.satsVisibleWentOOB",  "NTP GPS no longer enough satellites visible", "NTP GPS no longer enough satellites visible: " + _scraping.satsTotal, 6,
                "NTPServer.satsVisibleWentIB",   "NTP GPS now enough satellites visible",       "NTP GPS now enough satellites visible: "       + _scraping.satsTotal, 6 );

        // handle change in NTP GPS antenna ok or not ok...
        handleChangedCondition( () -> _scraping.antennaOK, () -> lastAntennaOK, (t) -> lastAntennaOK = t,
                "NTPServer.antennaNotOk",     "NTP GPS antenna is not ok",   "NTP GPS antenna is not ok",   7,
                "NTPServer.antennaOk",        "NTP GPS antenna is ok",       "NTP GPS antenna is ok",       2,
                "NTPServer.antennaWentNotOk", "NTP GPS antenna went not ok", "NTP GPS antenna went not ok", 7,
                "NTPServer.antennaWentOk",    "NTP GPS antenna went ok",     "NTP GPS antenna went ok",     7 );
    }


    /**
     * Send an event on change of a monitored condition.
     *
     * @param _current Function to return the current state of the monitored condition: true if ok, false if not.
     * @param _previous Function to return the state of the monitored condition on the previous monitor run: true if ok, false if not, unknown if this is the first monitor run.
     * @param _setPrevious Function to set the previous state of the monitored condition.
     * @param _tagTypeUF Event tag and type when condition changed from unknown to false.
     * @param _subjectUF Event subject when condition changed from unknown to false.
     * @param _msgUF Event message when condition changed from unknown to false.
     * @param _levelUF Event level when condition changed from unknown to false.
     * @param _tagTypeUT Event tag and type when condition changed from unknown to true.
     * @param _subjectUT Event subject when condition changed from unknown to true.
     * @param _msgUT Event message when condition changed from unknown to true.
     * @param _levelUT Event level when condition changed from unknown to true.
     * @param _tagTypeTF Event tag and type when condition changed from true to false.
     * @param _subjectTF Event subject when condition changed from true to false.
     * @param _msgTF Event message when condition changed from true to false.
     * @param _levelTF Event level when condition changed from true to false.
     * @param _tagTypeFT Event tag and type when condition changed from false to true.
     * @param _subjectFT Event subject when condition changed from false to true.
     * @param _msgFT Event message when condition changed from false to true.
     * @param _levelFT Event level when condition changed from false to true.
     */
    private void handleChangedCondition( final Supplier<Boolean> _current, final Supplier<TriState> _previous, final Consumer<TriState> _setPrevious,
                                         final String _tagTypeUF, String _subjectUF, String _msgUF, int _levelUF,
                                         final String _tagTypeUT, String _subjectUT, String _msgUT, int _levelUT,
                                         final String _tagTypeTF, String _subjectTF, String _msgTF, int _levelTF,
                                         final String _tagTypeFT, String _subjectFT, String _msgFT, int _levelFT ) {

        // get the current state as a tri-state...
        var current = from( _current.get() );

        // if the state has changed (previous state does not equal current state), send an event...
        if( current != _previous.get() ) {

            // if we didn't know the previous state (because the monitor program was just started)...
            if( _previous.get() == UNKNOWN ) {

                // send the appropriate event given the current state...
                if( current == FALSE )
                    sendEvent( _tagTypeUF, _tagTypeUF, _subjectUF, _msgUF, _levelUF );
                else
                    sendEvent( _tagTypeUT, _tagTypeUT, _subjectUT, _msgUT, _levelUT );
            }

            // if we did know the previous state...
            else {

                // send the appropriate event given the current state...
                if( current == FALSE )
                    sendEvent( _tagTypeTF, _tagTypeTF, _subjectTF, _msgTF, _levelTF );
                else
                    sendEvent( _tagTypeFT, _tagTypeFT, _subjectFT, _msgFT, _levelFT );
            }

            // set the previous state to the current state...
            _setPrevious.accept( current );
        }
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