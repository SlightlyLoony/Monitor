package com.dilatush.monitor;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;

import java.util.Date;

public class EventSender {

    private final Mailbox mailbox;


    public EventSender( final Mailbox _mailbox ) {
        mailbox = _mailbox;
    }

    public void sendEvent( final String _tag, final String _source, final String _subject, final String _type, final String _message, final int _level ) {
        Message msg = mailbox.createDirectMessage( "events.post", "event.post", false );
        msg.putDotted( "tag",          _tag               );
        msg.putDotted( "timestamp",    System.currentTimeMillis() );
        msg.putDotted( "event.source", _source              );
        msg.putDotted( "event.type",    _type      );
        msg.putDotted( "event.message", _message                   );
        msg.putDotted( "event.level",   _level                         );
        msg.putDotted( "event.subject", _subject                   );
        mailbox.send( msg );
    }
}
