package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static com.dilatush.util.General.isNull;

public abstract class AMonitor {

    protected final Mailbox             mailbox;
    protected final String              eventSource;
    protected final Map<String,Instant> tagLastSentMap;  // tag -> when last sent...


    protected AMonitor( final Mailbox _mailbox ) {

        if( isNull( _mailbox ) ) throw new IllegalArgumentException( "_mailbox must be provided" );

        mailbox         = _mailbox;
        eventSource     = "monitor." + getClass().getSimpleName();
        tagLastSentMap  = new HashMap<>();
    }


    protected void sendEvent( final Duration _minInterval, final String _tag, final String _type, final String _subject, final String _message, final int _level ) {

        // if we have a time an event with this tag was last sent...
        var timeSent = tagLastSentMap.get( _tag );
        if( timeSent != null ) {

            // if we're within the minimum interval between events, just leave...
            if( Instant.now().isBefore( timeSent.plus( _minInterval ) ) )
                return;
        }

        // if we get here, we should send the event and record the time we did so...
        sendEvent( _tag, _type, _subject, _message, _level );
        tagLastSentMap.put( _tag, Instant.now().plus( _minInterval ) );
    }


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


    /**
     * Perform the periodic monitoring.
     */
    public abstract void run();
}
