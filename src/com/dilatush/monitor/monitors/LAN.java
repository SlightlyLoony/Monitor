package com.dilatush.monitor.monitors;

import com.dilatush.monitor.Monitor;
import com.dilatush.mop.Mailbox;
import com.dilatush.util.ip.IPAddress;
import com.dilatush.util.networkingengine.TCPConnectionTest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;

/**
 * Implements connectivity monitoring of the Local Area Network (LAN), from the monitoring computer to the configured addresses and TCP ports.
 */
public class LAN extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private final List<Check> checks;
    private final Map<String,Boolean> lastStates;

    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox  The mailbox for this monitor to use.
     * @param _params The parameters for this monitor: "checks" which is a list of Check instances.
     * @param _interval The interval between runs for this monitor.
     */
    public LAN( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );
        //noinspection unchecked
        checks = (List<Check>) _params.get( "checks" );
        lastStates = new HashMap<>();
    }


    /**
     * Perform the periodic monitoring.  This monitor should  be run every 15 minutes.
     */
    @Override
    protected void runImpl() {

        // get our connection tester...
        var tester = new TCPConnectionTest( Monitor.getNetworkingEngine() );

        // run all the configured checks...
        for( Check check : checks ) {

            // if we have no last state for this check, default it to true (it connected)...
            if( !lastStates.containsKey( check.name ) ) lastStates.put( check.name, true );

            // try to connect...
            var result = tester.isConnectable( check.initialTimeoutMs, (ms) -> ms + check.initialTimeoutMs, 5, check.ip, check.port );

            // if we had a failure, log it and continue...
            if( result.notOk() ) {
                LOGGER.log( Level.WARNING, "TCP connection test failure: " + result.msg(), result.cause() );
                continue;
            }

            // if we had a change in state, send an event...
            if( result.info() != lastStates.get( check.name ) ) {
                var type = result.info() ? "LAN.connected" : "LAN.disconnected";
                var subject = "Host " + Monitor.getHost() + " is now " + (result.info() ? "connected to " : " disconnected from ") + check.name;
                var message = subject + " (" + check.ip.toString() + ":" + check.port + ").";
                sendEvent( type, check.name, subject, message, 8 );
            }
        }
    }


    public record Check( String name, IPAddress ip, int port, int initialTimeoutMs ){}
}
