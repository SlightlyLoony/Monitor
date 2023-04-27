package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static com.dilatush.util.General.isNull;

/**
 * Abstract base class for all monitors.
 */
public abstract class AMonitor implements Runnable {

    protected final Mailbox             mailbox;      // the mailbox for this monitor to use...
    protected final String              eventSource;  // the source for events from this monitor, in the form "monitor.<monitor class name>"...

    // keeps track of the last time we sent a rate-limited event...
    protected final Map<String,Instant> tagLastSentMap;  // tag -> when last sent...


    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox The mailbox for this monitor to use.
     */
    protected AMonitor( final Mailbox _mailbox ) {

        if( isNull( _mailbox ) ) throw new IllegalArgumentException( "_mailbox must be provided" );

        mailbox         = _mailbox;
        eventSource     = "monitor." + getClass().getSimpleName();
        tagLastSentMap  = new HashMap<>();
    }


    /**
     * Return the base-64 encoded basic authentication string for the given username and password.
     *
     * @param _user The username to encode.
     * @param _password The password to encode.
     * @return The base-64 encoded basic authentication string.
     */
    protected String getBasicAuthentication( final String _user, final String _password ) {
        var up = _user + ":" + _password;
        var b64e = Base64.getEncoder();
        return b64e.encodeToString( up.getBytes( StandardCharsets.UTF_8) );
    }


    /**
     * Send an event with the given minimum interval, the given tag, type, subject, message, and level, from the event source created by the constructor, with a timestamp of now.
     * If called with an event where an event with the same tag was sent less than the minimum interval, this method returns without sending the event.
     *
     * @param _minInterval The minimum interval between sending events with the given tag; if null then there is no minimum time.
     * @param _tag The tag for the event.
     * @param _type The type of the event.
     * @param _subject The subject for the event.
     * @param _message The message for the event.
     * @param _level The level for the event.
     */
    protected void sendEvent( final Duration _minInterval, final String _tag, final String _type, final String _subject, final String _message, final int _level ) {

        // if no minimum interval, just send the damn thing...
        if( isNull( _minInterval ) ) {
            sendEvent( _tag, _type, _subject, _message, _level );
            return;
        }

        // construct the index...
        var index = _tag + "." + _type;

        // if we have a time an event with this tag was last sent...
        var timeSent = tagLastSentMap.get( index );
        if( timeSent != null ) {

            // if we're within the minimum interval between events, just leave...
            if( Instant.now().isBefore( timeSent.plus( _minInterval ) ) )
                return;
        }

        // if we get here, we should send the event and record the time we did so...
        sendEvent( _tag, _type, _subject, _message, _level );
        tagLastSentMap.put( index, Instant.now().plus( _minInterval ) );
    }


    /**
     * Send an event with the given tag, type, subject, message, and level, from the event source created by the constructor, with a timestamp of now.
     *
     * @param _tag The tag for the event.
     * @param _type The type of the event.
     * @param _subject The subject for the event.
     * @param _message The message for the event.
     * @param _level The level for the event.
     */
    protected void sendEvent( final String _tag, final String _type, final String _subject, final String _message, final int _level ) {

        Message msg = mailbox.createDirectMessage( "events.post", "event.post", false );
        msg.putDotted( "tag",           _tag                       );
        msg.putDotted( "timestamp",     System.currentTimeMillis() );
        msg.putDotted( "event.source",  eventSource                );
        msg.putDotted( "event.type",    _type                      );
        msg.putDotted( "event.message", _message                   );
        msg.putDotted( "event.level",   _level                     );
        msg.putDotted( "event.subject", _subject                   );
        mailbox.send( msg );
    }


    protected enum TriState {

        UNKNOWN, TRUE, FALSE;

        public static TriState from( final boolean _b ) {
            return _b ? TRUE : FALSE;
        }
    }


    /**
     * Perform the periodic monitoring.
     */
    public abstract void run();
}
