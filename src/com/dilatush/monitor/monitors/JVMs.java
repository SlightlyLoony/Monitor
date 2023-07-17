package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.util.BashExecutor;

import java.time.Duration;
import java.util.*;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.Strings.isEmpty;

public class JVMs extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private final static String LIST_JVMS = "ps -ww -eo user,pid,comm,args | grep java | grep -v jsvc | grep -v grep | sed -r \"s:^.*/(.*)\\.jar.*$:\\1:\"";
    private static final Duration INTERVAL_BETWEEN_MISSING_JAR_EVENTS = Duration.ofHours( 12 );

    private final List<JVMInfo> expectedJVMs;   // a list of the JVMs we expect to be running...
    private final Set<String> missingJars;  // a set of all the jars that were missing last time we checked...

    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox  The mailbox for this monitor to use.
     * @param _params The parameters for this monitor.
     * @param _interval the interval between runs for this monitor.
     */
    public JVMs( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );
        
        missingJars = new HashSet<>();
        expectedJVMs = new ArrayList<>();

        // JVMs parameter is comma-separated list of colon-separated pairs (JarName:DisplayName)...
        var jvms = (String) _params.get( "JVMs" );
        if( isEmpty( jvms ) ) throw new IllegalArgumentException( "JVMs parameter is missing" );
        var pairs = jvms.split( "," );
        for( String pair : pairs ) {
            var parts = pair.split( ":" );
            var info = new JVMInfo( parts[0], parts[1] );
            expectedJVMs.add( info );
        }
    }


    @Override
    protected void runImpl() {

        // get a set of the jar names that are currently running...
        var exec = new BashExecutor( LIST_JVMS );
        var runningJarsRaw = exec.run();
        LOGGER.finest( runningJarsRaw );
        var runningJarsArray = runningJarsRaw.split( "\n" );
        var runningJars = new HashSet<>( Arrays.asList( runningJarsArray ) );

        // run through the expected jars to find our exceptions...
        for( JVMInfo expected : expectedJVMs ) {

            // if it's running now, but not previously, send the "back up" event...
            if( runningJars.contains( expected.jar ) ) {
                if( missingJars.contains( expected.jar ) ) {
                    missingJars.remove( expected.jar );
                    sendEvent( "jar.running", expected.jar, expected.display + " running", expected.display + " (" + expected.jar + ".jar) is running again", 8 );
                }
            }

            // otherwise, if it's not running now, send the "service down" event...
            else {
                missingJars.add( expected.jar );
                sendEvent( INTERVAL_BETWEEN_MISSING_JAR_EVENTS,
                        "jar.notRunning", expected.jar, expected.display + " not running", expected.display + " (" + expected.jar + ".jar) is not running", 8 );
            }
        }
    }


    private record JVMInfo( String jar, String display ) {}
}
