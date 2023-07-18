package com.dilatush.monitor.monitors;

import com.dilatush.monitor.Monitor;
import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;
import com.dilatush.util.Files;
import com.dilatush.util.Outcome;
import com.dilatush.util.Strings;
import com.dilatush.util.Time;
import com.dilatush.util.ip.IPAddress;
import com.dilatush.util.networkingengine.TCPConnectionTest;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.HTTP.requestJSONText;
import static com.dilatush.util.HTTP.requestText;

/**
 * Implements monitoring of primary and secondary ISP.  It is hardwired for ISP characteristics at the author's (Tom Dilatush's) house, and should run
 * every 15 seconds.  It sends status messages, events, and statistics.
 */
public class ISP extends AMonitor {

    private static final Logger LOGGER = getLogger();
    @SuppressWarnings( "SpellCheckingInspection" )
    private static final DateTimeFormatter localDateTimeFormat = DateTimeFormatter.ofPattern( "LLL dd, uuuu hh:mm:ss.SSS" );

    private static final Outcome.Forge<IPAddress> FORGE_IP       = new Outcome.Forge<>();
    private static final Outcome.Forge<ISPInfo>   FORGE_ISP_INFO = new Outcome.Forge<>();

    private static final IPAddress STARLINK_ONLY_IP = IPAddress.fromString( "208.67.220.220" ).info();
    private static final IPAddress VERIZON_ONLY_IP  = IPAddress.fromString( "208.67.222.222" ).info();
    private static final IPAddress EDGE_ROUTER_IP   = IPAddress.fromString( "10.1.4.1"       ).info();
    private static final File      PERSISTENCE_FILE = new File( "isp.data" );

    private static final int DNS_PORT           = 53;
    private static final int ROUTER_PORT        = 80;
    private static final int CONNECT_TIMEOUT_MS = 1000;

    // statistics...
    private Duration  onPrimaryTime;      // amount of time on primary ISP...
    private Duration  onSecondaryTime;    // amount of time on secondary ISP...
    private double    onPrimaryPct;       // percent of time on primary ISP...
    private long      onSecondaryCount;   // number of times on secondary ISP...
    private Duration  downPrimaryTime;    // amount of time primary ISP is down...
    private Duration  downSecondaryTime;  // amount of time secondary ISP is down...
    private Instant   lastSecondaryTime;  // last time on secondary ISP started...
    private Instant   lastCaptureTime;    // last time we captured ISP data...
    private ISPRank   lastRank;           // the ISP rank last captured...
    private boolean   primaryUp;          // true if primary ISP is up...
    private boolean   secondaryUp;        // true if secondary ISP is up...
    private IPAddress ipAddress;          // the public IP address...

    // cache of ISP information by public IP...
    private final Map<IPAddress,ISPInfo> isps = new HashMap<>();

    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox  The mailbox for this monitor to use.
     * @param _params The parameters for this monitor (none).
     * @param _interval The interval between runs for this monitor.
     */
    public ISP( final Mailbox _mailbox, @SuppressWarnings( "unused" ) final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );

        // initialize our statistics...
        onPrimaryTime     = Duration.ZERO;
        onSecondaryTime   = Duration.ZERO;
        onPrimaryPct      = 0;
        onSecondaryCount = 0;
        downPrimaryTime   = Duration.ZERO;
        downSecondaryTime = Duration.ZERO;
        lastSecondaryTime = null;
        lastCaptureTime   = null;
        lastRank          = ISPRank.UNKNOWN;
        primaryUp         = false;
        secondaryUp       = false;
        ipAddress         = null;

        // if we have a persistence file, read it to recover the statistics state...
        if( PERSISTENCE_FILE.exists() )  {

            // read our CSV persistence file...
            var persistenceFile = Files.readToString( PERSISTENCE_FILE );
            if( persistenceFile == null ) {
                LOGGER.warning( "Could not read persistence file: " + PERSISTENCE_FILE );
                throw new IllegalStateException( "Could not read persistence file: " + PERSISTENCE_FILE );
            }

            // get the individual values and parse them...
            try {
                var values = persistenceFile.split( "," );
                onPrimaryTime     = Duration.ofMillis( Long.parseLong( values[0] ) );
                onSecondaryTime   = Duration.ofMillis( Long.parseLong( values[1] ) );
                onPrimaryPct      = Double.parseDouble( values[2] );
                onSecondaryCount = Long.parseLong( values[3] );
                downPrimaryTime   = Duration.ofMillis( Long.parseLong( values[4] ) );
                downSecondaryTime = Duration.ofMillis( Long.parseLong( values[5] ) );
                lastSecondaryTime = "null".equals( values[6] ) ? null : Instant.ofEpochMilli( Long.parseLong( values[6] ) );
                lastCaptureTime   = "null".equals( values[7] ) ? null : Instant.ofEpochMilli( Long.parseLong( values[7] ) );
                lastRank          = ISPRank.valueOf( values[8] );
                primaryUp         = Boolean.parseBoolean( values[9] );
                secondaryUp       = Boolean.parseBoolean( values[10] );
                ipAddress         = "null".equals(values[11]) ? null : IPAddress.fromString( values[11] ).info();
            }
            catch( Exception _e ) {
                LOGGER.log( Level.WARNING, "Problem reading persistence file: " + _e.getMessage(), _e );
                throw new IllegalArgumentException( "Problem reading persistence file: " + _e.getMessage(), _e );
            }
        }
    }


    /**
     * Perform the periodic monitoring.  This monitor should  be run every 15 seconds.
     */
    @Override
    protected void runImpl() {

        var startTime = Instant.now();

        // make sure we've got connectivity to our edge router...
        var edgeOutcome = isEdgeRouterUp();
        if( edgeOutcome.notOk() ) {
            LOGGER.log( Level.WARNING, "Problem connecting to edge router: " + edgeOutcome.msg(), edgeOutcome.cause() );
            return;
        }
        if( !edgeOutcome.info() ) {
            LOGGER.log( Level.WARNING, "Timed out connecting to edge router" );
            return;
        }

        // we have connectivity to our edge router, so now get our public IP address...
        var publicIPOutcome = getPublicIP();
        if( publicIPOutcome.notOk() ) {
            LOGGER.log( Level.WARNING, "Problem getting public IP address: " + publicIPOutcome.msg(), publicIPOutcome.cause() );
            return;
        }

        // we have our public IP address, so now get info on the ISP...
        var ispOutcome = getISPInfo( publicIPOutcome.info() );
        if( ispOutcome.notOk() ) {
            LOGGER.log( Level.WARNING, "Problem getting ISP information: " + ispOutcome.msg(), ispOutcome.cause() );
            return;
        }

        // if we make it here, we've got ISP information, and we're committed to a capture...
        var ispInfo = ispOutcome.info();
        var captureTime = Instant.now();
        var ipuOutcome = isPrimaryUp();
        var primaryNowUp = ipuOutcome.ok() ? ipuOutcome.info() : primaryUp;  // update if we got a good reading; otherwise use the last good reading...
        var isuOutcome = isSecondaryUp();
        var secondaryNowUp = isuOutcome.ok() ? isuOutcome.info() : secondaryUp;  // update if we got a good reading; otherwise use the last good reading...
        var toPri = (ispInfo.rank == ISPRank.PRIMARY) && (lastRank != ISPRank.PRIMARY);
        var toSec = (ispInfo.rank == ISPRank.SECONDARY) && (lastRank != ISPRank.SECONDARY);
        if( toSec ) lastSecondaryTime = captureTime;

        // and from the captured info, the statistics...
        var ipChange = (ipAddress == null) || !ipAddress.equals( ispInfo.ip );
        var delta = (lastCaptureTime == null) ? Duration.ZERO : Duration.ofMillis( captureTime.toEpochMilli() - lastCaptureTime.toEpochMilli() );
        if( ispInfo.rank == ISPRank.PRIMARY ) onPrimaryTime = onPrimaryTime.plus( delta );
        if( ispInfo.rank == ISPRank.SECONDARY ) onSecondaryTime = onSecondaryTime.plus( delta );
        onPrimaryPct = (onPrimaryTime.toMillis() == 0) ? 100D : 100D * onPrimaryTime.toMillis() / (onPrimaryTime.toMillis() + onSecondaryTime.toMillis() );
        onSecondaryCount += (toSec ? 1 : 0);
        if( !primaryNowUp ) downPrimaryTime = downPrimaryTime.plus( delta );
        if( !secondaryNowUp ) downSecondaryTime = downSecondaryTime.plus( delta );
        var priWentUp = !primaryUp && primaryNowUp;
        var priWentDown = primaryUp && !primaryNowUp;
        primaryUp = primaryNowUp;
        var secWentUp = !secondaryUp && secondaryNowUp;
        var secWentDown = secondaryUp && !secondaryNowUp;
        secondaryUp = secondaryNowUp;
        lastCaptureTime = captureTime;
        lastRank = ispInfo.rank;

        // and from the statistics, our events...
        if( toPri       ) sendToPrimaryEvent();
        if( toSec       ) sendToSecondaryEvent();
        if( priWentUp   ) sendPrimaryWentUpEvent();
        if( priWentDown ) sendPrimaryWentDownEvent();
        if( secWentUp   ) sendSecondaryWentUpEvent();
        if( secWentDown ) sendSecondaryWentDownEvent();
        if( ipChange    ) sendIPChangeEvent( ipAddress, ispInfo.ip );

        ipAddress = ispInfo.ip;

        // send the status message...
        sendStatus();

        // send the database update on any change...
        var dbChange = ipChange || toPri || toSec || priWentUp || priWentDown || secWentDown || secWentUp;
        if( dbChange ) sendDbUpdate();

        // persist the statistics...
        saveStatistics();

        var runTime = Instant.now().toEpochMilli() - startTime.toEpochMilli();
        LOGGER.finest( "ISP Monitor run time: " + Time.formatDuration( runTime ) );
    }


    private void sendDbUpdate() {

        // build our event message...
        Message msg = mailbox.createDirectMessage( "events.post", "event.post", false );
        msg.putDotted( "tag",                 "ISP_stats"                 );
        msg.putDotted( "timestamp",           System.currentTimeMillis()  );
        msg.putDotted( "fields.primaryUp",    primaryUp                   );
        msg.putDotted( "fields.secondaryUp",  secondaryUp                 );
        msg.putDotted( "fields.ispRank",      lastRank.toString()         );
        msg.putDotted( "fields.isp",          isps.get( ipAddress ).name  );
        msg.putDotted( "fields.publicIP",     ipAddress.toString()        );
        msg.putDotted( "fields.onPrimaryPct", onPrimaryPct                );

        // send it!
        mailbox.send( msg );
        LOGGER.info( "Sent ISP statistics message" );
    }


    private void sendStatus() {

        // format the last secondary time...
        String lastSecondaryTimeFormatted = "unknown";
        if( lastSecondaryTime != null ) {
            var zs = ZonedDateTime.ofInstant( lastSecondaryTime, ZoneId.of( "America/Denver" ) );
            lastSecondaryTimeFormatted = localDateTimeFormat.format( zs );
        }

        Message msg = mailbox.createPublishMessage( "isp.monitor" );

        // send the message interval...
        msg.putDotted( "monitor.isp.messageIntervalMs", interval.toMillis()                                            );

        // fill in our collected data...
        msg.putDotted( "monitor.isp.publicIP",          ipAddress.toString() + " (" + isps.get( ipAddress ).name + ")" );
        msg.putDotted( "monitor.isp.primaryUp",         primaryUp                                                      );
        msg.putDotted( "monitor.isp.secondaryUp",       secondaryUp                                                    );
        msg.putDotted( "monitor.isp.onPrimaryPct",      onPrimaryPct                                                   );
        msg.putDotted( "monitor.isp.onSecondaryTimeMs", onSecondaryTime.toMillis()                                     );
        msg.putDotted( "monitor.isp.onSecondaryCount",  onSecondaryCount                                               );
        msg.putDotted( "monitor.isp.onPrimaryTimeMs",   onPrimaryTime.toMillis()                                       );
        msg.putDotted( "monitor.isp.lastSecondary",     lastSecondaryTimeFormatted                                     );

        // send it!
        mailbox.send( msg );
        LOGGER.info( "Sent ISP monitor message" );
    }


    private void sendToPrimaryEvent() {
        var secTime = (lastSecondaryTime == null) ?
                "an unknown period" :
                Time.formatDuration( lastCaptureTime.toEpochMilli() - lastSecondaryTime.toEpochMilli() );
        sendEvent( "ISP.toPrimary", "ISP.toPrimary", "Effective ISP switched to Starlink (primary)",
                "Switched to Starlink after " + secTime + " on Verizon (secondary).", 9 );
    }


    private void sendToSecondaryEvent() {
        var priTime = (lastSecondaryTime == null) ?
                "an unknown period" :
                Time.formatDuration( lastCaptureTime.toEpochMilli() - lastSecondaryTime.toEpochMilli() );
        sendEvent( "ISP.toSecondary", "ISP.toSecondary", "Effective ISP switched to Verizon (secondary)",
                "Switched to Verizon after about " + priTime + " on Starlink (primary).", 9 );
    }


    private void sendPrimaryWentUpEvent() {
        sendEvent( "ISP.primaryWentUp", "ISP.primaryWentUp", "Starlink (primary ISP) is now up",
                "Starlink (primary ISP) is now up.", 9 );
    }


    private void sendPrimaryWentDownEvent() {
        sendEvent( "ISP.primaryWentDown", "ISP.primaryWentDown", "Starlink (primary ISP) is now down",
                "Starlink (primary ISP) is now down.", 9 );
    }


    private void sendSecondaryWentUpEvent() {
        sendEvent( "ISP.secondaryWentUp", "ISP.secondaryWentUp", "Verizon (secondary ISP) is now up",
                "Verizon (secondary ISP) is now up.", 9 );
    }


    private void sendSecondaryWentDownEvent() {
        sendEvent( "ISP.secondaryWentDown", "ISP.secondaryWentDown", "Verizon (secondary ISP) is now down",
                "Verizon (secondary ISP) is now down.", 9 );
    }


    private void sendIPChangeEvent( final IPAddress _from, final IPAddress _to ) {
        String msg;
        if( _from == null )
            msg = "Public IP address was unknown, is now " + _to.toString() + " (" + isps.get( _to ).name + ")";
        else
            msg = "Public IP address was " + _from + " (" + isps.get( _from ).name + "), is now " +
                    _to.toString() + " (" + isps.get( _to ).name + ")";
        sendEvent( "ISP.publicIPChange", "ISP.publicIPChange", "Public IP address changed to: " + _to,
                msg, 6 );
    }


    /**
     * Attempts to establish a TCP connection to the edge router's management website.
     *
     * @return The outcome of the attempt.  If ok, returns {@code true} for a successful attempt and {@code false} if the attempt timed out an all three attempts.
     * If not ok, then there is an explanatory message and possibly the exception that caused the problem.
     */
    private Outcome<Boolean> isEdgeRouterUp() {
        var outcome = isConnectable( EDGE_ROUTER_IP, ROUTER_PORT );
        if( outcome.ok() )
            LOGGER.finest( outcome.info() ? "Connected to edge router" : "Timed out when attempting to connect to edge router" );
        else
            LOGGER.info( "Problem connecting to edge router: " + outcome.msg() );
        return outcome;
    }


    /**
     * Attempts to establish a TCP connection to a DNS server that is accessible only through Starlink.
     *
     * @return The outcome of the attempt.  If ok, returns {@code true} for a successful attempt and {@code false} if the attempt timed out an all three attempts.
     * If not ok, then there is an explanatory message and possibly the exception that caused the problem.
     */
    private Outcome<Boolean> isPrimaryUp() {
        var outcome = isConnectable( STARLINK_ONLY_IP, DNS_PORT );
        if( outcome.ok() )
            LOGGER.finest( outcome.info() ? "Connected through Starlink" : "Timed out when attempting to connect through Starlink" );
        else
            LOGGER.info( "Problem connecting through Starlink: " + outcome.msg() );
        return outcome;
    }


    /**
     * Attempts to establish a TCP connection to a DNS server that is accessible only through Verizon.
     *
     * @return The outcome of the attempt.  If ok, returns {@code true} for a successful attempt and {@code false} if the attempt timed out an all three attempts.
     * If not ok, then there is an explanatory message and possibly the exception that caused the problem.
     */
    private Outcome<Boolean> isSecondaryUp() {
        var outcome = isConnectable( VERIZON_ONLY_IP, DNS_PORT );
        if( outcome.ok() )
            LOGGER.finest( outcome.info() ? "Connected through Verizon" : "Timed out when attempting to connect through Verizon" );
        else
            LOGGER.info( "Problem connecting through Verizon: " + outcome.msg() );
        return outcome;
    }


    /**
     * Attempts to establish a TCP connection to the given IP address and port.  This method will try up to three times to make the connection, then give up.
     *
     * @param _ip The IP address for the connection attempt.
     * @param _port The TCP port for the connection attempt.
     * @return The outcome of the attempt.  If ok, returns {@code true} for a successful attempt and {@code false} if the attempt timed out an all three attempts.
     * If not ok, then there is an explanatory message and possibly the exception that caused the problem.
     */
    private Outcome<Boolean> isConnectable( final IPAddress _ip, final int _port ) {

        var tcp = new TCPConnectionTest( Monitor.getNetworkingEngine() );
        return tcp.isConnectable( CONNECT_TIMEOUT_MS, (ms) -> ms + CONNECT_TIMEOUT_MS, 3, _ip, _port );
    }


    /**
     * Return the public IP address for requests made from this machine.  This is the IP address provided by the ISP.  It may change from time to time, and it
     * <i>will</i> change if the ISP changes.
     *
     * @return The outcome of this operation.  If successful, the outcome will be ok, and will contain the public IP address.  If unsuccessful, the outcome will
     * contain an explanatory message and possible the exception that caused the problem.
     */
    private Outcome<IPAddress> getPublicIP() {

        @SuppressWarnings( "HttpUrlsUsage" ) var reqOutcome = requestText( "http://checkip.amazonaws.com" );
        if( reqOutcome.notOk() ) return FORGE_IP.notOk( reqOutcome );
        return IPAddress.fromString( Strings.stripTrailingNewlines( reqOutcome.info() ) );
    }


    /**
     * Queries ISP information for the given public IP address.  Note that this is very limited checker - it merely checks for the presence of words like
     * "SpaceX" or "Verizon"; if none appear then it returns unknown.
     *
     * @param _ip The IP address to get information for.
     * @return The outcome of the query.  If ok, contains the ISP information.  If not ok, contains an explanatory message and possibly the exception that
     * caused the problem.
     */
    private Outcome<ISPInfo> getISPInfo( final IPAddress _ip ) {

        synchronized( isps ) {

            // have we ever seen this IP before?
            if( isps.containsKey( _ip ) ) {

                // yes, so return the ISP info we already figured out...
                return FORGE_ISP_INFO.ok( isps.get( _ip ) );
            }

            // no, so query for it in ARIN's registry...
            @SuppressWarnings( "HttpUrlsUsage" ) var reqOutcome = requestJSONText( "http://rdap.arin.net/registry/ip/" + _ip.toString() );
            if( reqOutcome.notOk() ) return FORGE_ISP_INFO.notOk( reqOutcome );
            var response = reqOutcome.info().toLowerCase();

            // make our ISP record...
            var isp = (response.contains( "starlink" ) || response.contains( "spacex" )) ?
                new ISPInfo( _ip, ISPRank.PRIMARY, "Starlink" ) :
                (response.contains( "verizon" ) ?
                        new ISPInfo( _ip, ISPRank.SECONDARY, "Verizon" ) :
                        new ISPInfo( _ip, ISPRank.UNKNOWN, "Unknown" ) );

            // update our cache...
            isps.put( _ip, isp );

            // return the answer...
            return FORGE_ISP_INFO.ok( isp );
        }
    }


    /**
     * Save statistics to a persistent file.
     */
    private void saveStatistics() {
        var stats = new ArrayList<String>();
        stats.add( Long.toString( onPrimaryTime.toMillis() ) );
        stats.add( Long.toString( onSecondaryTime.toMillis() ) );
        stats.add( Double.toString( onPrimaryPct ) );
        stats.add( Long.toString( onSecondaryCount ) );
        stats.add( Long.toString( downPrimaryTime.toMillis() ) );
        stats.add( Long.toString( downSecondaryTime.toMillis() ) );
        stats.add( (lastSecondaryTime == null) ? "null" : Long.toString( lastSecondaryTime.toEpochMilli() ) );
        stats.add( (lastCaptureTime == null) ? "null" : Long.toString( lastCaptureTime.toEpochMilli() ) );
        stats.add( lastRank.name() );
        stats.add( Boolean.toString( primaryUp ) );
        stats.add( Boolean.toString( secondaryUp ) );
        stats.add( (ipAddress == null) ? "null" : ipAddress.toString() );
        var statsStr = String.join( ",", stats );
        if( !Files.writeToFile( PERSISTENCE_FILE, statsStr ) ) {
            LOGGER.severe( "Failed to write ISP statistics persistence file: " + PERSISTENCE_FILE );
        }
    }


    public record ISPInfo( IPAddress ip, ISPRank rank, String name ){}

    public enum ISPRank { PRIMARY, SECONDARY, UNKNOWN }
}
