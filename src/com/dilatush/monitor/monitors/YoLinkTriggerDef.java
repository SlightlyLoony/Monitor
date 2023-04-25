package com.dilatush.monitor.monitors;

import java.time.Duration;

import static com.dilatush.util.General.isNull;
import static com.dilatush.util.Strings.isEmpty;

public record YoLinkTriggerDef(
        String sensorName, double lowerBound, double upperBound,
        YoLinkTriggerType type, YoLinkTriggerField field, YoLinkTriggerClass klass,
        String eventTag, String eventSubject, String eventMessage, int eventLevel, Duration minInterval ) {


    public YoLinkTriggerDef {
        if( isEmpty( sensorName ) )                throw new IllegalArgumentException( "sensorName missing"                               );
        if( isNull( type ) )                       throw new IllegalArgumentException( "type missing"                                     );
        if( isNull( field ) )                      throw new IllegalArgumentException( "field missing"                                    );
        if( isNull( klass ) )                      throw new IllegalArgumentException( "klass missing"                                    );
        if( isEmpty( eventTag ) )                  throw new IllegalArgumentException( "eventTag missing"                                 );
        if( isEmpty( eventSubject ) )              throw new IllegalArgumentException( "eventSubject missing"                             );
        if( isEmpty( eventMessage ) )              throw new IllegalArgumentException( "eventMessage missing"                             );
        if( (eventLevel < 0) || (eventLevel > 9) ) throw new IllegalArgumentException( "eventLevel out-of-bounds ([0..9]): " + eventLevel );
    }


    public YoLinkTriggerDef(
            String sensorName, double lowerBound,
            YoLinkTriggerType type, YoLinkTriggerField field, YoLinkTriggerClass klass,
            String eventTag, String eventSubject, String eventMessage, int eventLevel, Duration minInterval ) {
        this( sensorName, lowerBound, lowerBound, type, field, klass, eventTag, eventSubject, eventMessage, eventLevel, minInterval );
    }
}

