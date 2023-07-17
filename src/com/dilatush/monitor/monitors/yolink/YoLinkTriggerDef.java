package com.dilatush.monitor.monitors.yolink;

import java.time.Duration;

import static com.dilatush.util.General.isNull;
import static com.dilatush.util.Strings.isEmpty;


/**
 * Instances of this class define a single trigger for an event based on readings from YoLink temperature and humidity sensors.
 */
@SuppressWarnings( "unused" )
public record YoLinkTriggerDef(
        String sensorName, double lowerBound, double upperBound,
        YoLinkTriggerType type, YoLinkTriggerField field, YoLinkTriggerClass klass,
        String eventTag, String eventSubject, String eventMessage, int eventLevel, Duration minInterval ) {


    /**
     * Create a new instance of this class with the given values.
     *
     * @param sensorName The user-defined name(s) (as they appear in the YoLink app) of the sensor(s) whose reading triggers this event.  This field may contain a
     *                   single device name, a comma-separated list of device names, or a single "?" (which means all sensors).
     * @param lowerBound The lower bound for IN and OUT comparisons, and the bound for LOWER comparisons.
     * @param upperBound The upper bound for IN and OUT comparisons, and the bound for ABOVE comparisons.
     * @param type The type of comparison: EQUAL, UNEQUAL, IN, OUT, ABOVE, or BELOW.
     * @param field The field (TEMPERATURE, HUMIDITY, BATTERY, or ONLINE) to compare.  TEMPERATURE is in °F, HUMIDITY in % relative humidity, BATTERY an integer
     *              value in [0..4] (where 4 is full battery), and ONLINE is 0 if the sensor is offline or 1 if it is online.
     * @param klass The class of trigger: TRANSITION (from false to true comparison on sequential readings) or VALUE (current reading).
     * @param eventTag The MOP event tag for the event triggered.
     * @param eventSubject The MOP event subject for the event triggered.  This is a {@code printf} string with the values for the current reading, the lower bound, and the upper
     *                     bound are doubles, sensor name is a string, all available in the given order.  Generally this should be kept to 60 characters or so in length.
     * @param eventMessage The MOP event message for the event triggered.  This is a {@code printf} string with the values for the current reading, the lower bound, and the upper
     *                     bound are doubles, sensor name is a string, all available in the given order.  There's no particular limit on the length of this message.
     * @param eventLevel The MOP event level for the event triggered.
     * @param minInterval Optional; if present it defines the minimum interval between events triggered.
     */
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


    /**
     * Create a new instance of this class with the given values.
     *
     * @param sensorName The user-defined name(s) (as they appear in the YoLink app) of the sensor(s) whose reading triggers this event.  This field may contain a
     *                   single device name, a comma-separated list of device names, or a single "?" (which means all sensors).
     * @param bound The lower and upper bound for IN and OUT comparisons, and the bound for ABOVE or LOWER comparisons.
     * @param type The type of comparison: EQUAL, UNEQUAL, IN, OUT, ABOVE, or BELOW.
     * @param field The field (TEMPERATURE, HUMIDITY, BATTERY, or ONLINE) to compare.  TEMPERATURE is in °F, HUMIDITY in % relative humidity, BATTERY an integer
     *              value in [0..4] (where 4 is full battery), and ONLINE is 0 if the sensor is offline or 1 if it is online.
     * @param klass The class of trigger: TRANSITION (from false to true comparison on sequential readings) or VALUE (current reading).
     * @param eventTag The MOP event tag for the event triggered.
     * @param eventSubject The MOP event subject for the event triggered.  This is a {@code printf} string with the values for the current reading, the lower bound, and the upper
     *                     bound are doubles, sensor name is a string, all available in the given order.  Generally this should be kept to 60 characters or so in length.
     * @param eventMessage The MOP event message for the event triggered.  This is a {@code printf} string with the values for the current reading, the lower bound, and the upper
     *                     bound are doubles, sensor name is a string, all available in the given order.  There's no particular limit on the length of this message.
     * @param eventLevel The MOP event level for the event triggered.
     * @param minInterval Optional; if present it defines the minimum interval between events triggered.
     */
    public YoLinkTriggerDef(
            String sensorName, double bound,
            YoLinkTriggerType type, YoLinkTriggerField field, YoLinkTriggerClass klass,
            String eventTag, String eventSubject, String eventMessage, int eventLevel, Duration minInterval ) {
        this( sensorName, bound, bound, type, field, klass, eventTag, eventSubject, eventMessage, eventLevel, minInterval );
    }
}

