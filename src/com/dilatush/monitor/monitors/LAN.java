package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.util.Outcome;
import com.dilatush.util.ip.IPAddress;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;

/**
 * Implements connectivity monitoring of the Local Area Network (LAN), from the monitoring computer to the configured addresses and TCP ports.
 */
public class LAN extends AMonitor {

    private static final Logger LOGGER = getLogger();
    @SuppressWarnings( "SpellCheckingInspection" )
    private static final DateTimeFormatter localDateTimeFormat = DateTimeFormatter.ofPattern( "LLL dd, uuuu hh:mm:ss.SSS" );

    private static final Outcome.Forge<IPAddress> FORGE_IP       = new Outcome.Forge<>();

    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox  The mailbox for this monitor to use.
     * @param _params The parameters for this monitor: "check" which is a list of Check instances.
     * @param _interval The interval between runs for this monitor.
     */
    public LAN( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );

    }


    /**
     * Perform the periodic monitoring.  This monitor should  be run every 15 seconds.
     */
    @Override
    protected void runImpl() {
    }


    public record Check( String name, IPAddress ip, int port, int initialTimeoutMs ){}
}
