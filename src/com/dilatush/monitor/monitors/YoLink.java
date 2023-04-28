package com.dilatush.monitor.monitors;

import com.dilatush.monitor.Config;
import com.dilatush.mop.Mailbox;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.Files;
import com.dilatush.util.Outcome;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.dilatush.util.Conversions.fromCtoF;
import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.General.isNull;
import static java.lang.Thread.sleep;


/**
 * Instances of this class implement a monitor for YoLink temperature and humidity sensors.
 */
public class YoLink extends AMonitor {

    // TODO: add statistics to table through events...
    // TODO: add status to web site...
    // TODO: add EQUALS...
    // TODO: finish commenting...
    // TODO: handle status for offline sensors...
    // TODO: handle statistics for offline sensors...
    // TODO: get new sensors for barn, shed, house...
    // TODO: get YoLink events into database...

    private final Logger LOGGER = getLogger();

    private final String clientID;
    private final String secret;

    private final List<YoLinkTriggerDef> triggers;
    private final Map<String,Boolean> previous;

    private String  accessToken;
    private Instant accessTokenExpires;


    /**
     * Create a new instance of this class, with the given mailbox and parameters.  At a minimum, the parameters must include "clientID" and "secret".  The parameters may
     * optionally include specifications for events triggered by reading from the sensors, as "triggers" mapped to a list of {@link YoLinkTriggerDef}s.
     *
     * @param _mailbox The MOP mailbox for this monitor to use.
     * @param _params The parameters for this monitor.
     */
    public YoLink( final Mailbox _mailbox, final Map<String,Object> _params ) {
        super( _mailbox );

        if( isNull( _params ) ) throw new IllegalArgumentException( "_params must be provided" );

        clientID  = (String) _params.get( "clientID" );
        secret    = (String) _params.get( "secret"   );
        var triggersParam = _params.get( "triggers" );

        previous = new HashMap<>();

        if( isNull( clientID, secret ) ) throw new IllegalArgumentException( "clientID and secret parameters must be supplied" );

        triggers = new ArrayList<>();

        if( triggersParam != null ) {

            if( !(triggersParam instanceof List<?> triggersRaw) ) throw new IllegalArgumentException( "triggers parameter supplied, but is not a List" );

            for( var trig : triggersRaw ) {

                if( !(trig instanceof YoLinkTriggerDef trigger) ) throw new IllegalArgumentException( "triggers list contains something other than a YoLinkTriggerDef" );

                triggers.add( trigger );
            }
        }
    }


    /**
     * Perform the periodic monitoring.
     */
    @Override
    public void run() {

        try {
            ensureAccessToken();
            var devices = getDevices();
            var states = getTempHumiditySensorsState( devices );

            // make a map of the sensors by name...
            var statesByName = new HashMap<String,THState>();
            for( var state : states ) statesByName.put( state.device.name, state );

            sendEvents( states, statesByName );
            sendStatus( states, statesByName );
        }
        catch( Exception _e ) {

            LOGGER.log( Level.SEVERE, "Failed while querying YoLink API: " + _e.getMessage(), _e );

            sendEvent( "YoLink.apiFail", "?", "Failure querying YoLink API", "Failure while querying YoLink API: " + _e.getMessage(), 8 );
        }
    }


    private void sendStatus( final List<THState> _states, final Map<String,THState> _statesByName ) {

    }


    private void sendEvents( final List<THState> _states, final Map<String,THState> _statesByName ) {

        // iterate over all our triggers...
        for( YoLinkTriggerDef trigger : triggers ) {

            // iterate over all sensors, or just the named sensor...
            var sensorStates = new ArrayList<String>();
            if( "?".equals( trigger.sensorName() ) )
                for( var state : _states ) sensorStates.add( state.device.name );
            else
                sensorStates.addAll( Arrays.asList( trigger.sensorName().split( "," ) ) );
            for( var sensorName : sensorStates ) {

                LOGGER.finest( "Working on " + sensorName + " for trigger " + trigger.eventTag() );

                // get the sensor state...
                var sensorState = _statesByName.get( sensorName );

                // if there is no sensor state, or if this sensor is offline, and we're not checking the online field, bail out...
                if( (sensorState == null) || ((!sensorState.online) && (trigger.field() != YoLinkTriggerField.ONLINE) ) )
                    continue;

                // get the current value...
                var value = getCurrentValue( sensorState, trigger.field() );

                // get the previous and current trigger values...
                var prevTriggerName = trigger.eventTag() + "." + sensorName;
                var prevTrigger = previous.get( prevTriggerName );
                var currTrigger = evaluateTrigger( value, trigger );
                if( prevTrigger == null ) prevTrigger = currTrigger;

                // figure out whether to send an event...
                var sendIt =
                        ((trigger.klass() == YoLinkTriggerClass.VALUE) && currTrigger) ||
                                ((trigger.klass() == YoLinkTriggerClass.TRANSITION) && currTrigger && !prevTrigger);
                if( sendIt ) {
                    sendEvent( value, sensorName, trigger );
                    LOGGER.finest( "Sent event " + trigger.eventTag() + " for " + sensorName );
                }

                // update our previous trigger value...
                previous.put( prevTriggerName, currTrigger );
            }
        }
    }


    private void sendEvent( final double _value, final String _sensorName, final YoLinkTriggerDef _trigger ) {

        // construct the subject and message, which may include the current value, lower bound, and upper bound...
        var subject = String.format( _trigger.eventSubject(), _value, _trigger.lowerBound(), _trigger.upperBound(), _sensorName );
        var message = String.format( _trigger.eventMessage(), _value, _trigger.lowerBound(), _trigger.upperBound(), _sensorName );

        // send the event...
        sendEvent( _trigger.minInterval(), _trigger.eventTag(), _sensorName, subject, message, _trigger.eventLevel() );
    }


    private boolean evaluateTrigger( final double _value, final YoLinkTriggerDef _trigger ) {
        return switch( _trigger.type() ) {
            case IN       -> (_value >= _trigger.lowerBound() && (_value <= _trigger.upperBound() ) );
            case OUT      -> (_value <  _trigger.lowerBound() || (_value >  _trigger.upperBound() ) );
            case BELOW    -> (_value <  _trigger.lowerBound() );
            case ABOVE    -> (_value >  _trigger.upperBound() );
        };
    }


    /**
     * Returns the current value of the given sensor and field.  The value returned depends on the field:
     * <ul>
     *     <li>HUMIDITY: relative percentage, from 0.0 to 100.0.</li>
     *     <li>TEMPERATURE: degrees Fahrenheit, from -20.0°F to 120.0°F.</li>
     *     <li>BATTERY: levels of 0, 1, 2, 3, or 4.</li>
     *     <li>ONLINE: 0 for offline, 1 for online.</li>
     * </ul>
     *
     * @param _state The current state of the sensor.
     * @param _field The field whose value is desired: HUMIDITY, TEMPERATURE, BATTERY, or ONLINE.
     * @return The value of the given field in the given sensor state.
     */
    private double getCurrentValue( final THState _state, final YoLinkTriggerField _field ) {
        return switch( _field ) {
            case HUMIDITY    -> _state.humidity;
            case TEMPERATURE -> _state.temperature;
            case BATTERY     -> _state.battery;
            case ONLINE      -> _state.online ? 1 : 0;
        };
    }


    private List<THState> getTempHumiditySensorsState( final List<Device> _devices ) throws IOException, JSONException {

        var result = new ArrayList<THState>();

        for( Device device : _devices ) {

            // get the state...
            var req = new JSONObject();
            req.put( "method", "THSensor.getState" );
            req.put( "targetDevice", device.id );
            req.put( "token", device.token );
            var resp = post( "https://api.yosmart.com/open/yolink/v2/api", req.toString(), "application/json", true );

            // if we don't see success, throw an exception...
            if( !"Success".equals( resp.get( "desc" ) ) ) throw new IOException( "YoLink failed to return device state" );

            var dataObj = resp.getJSONObject( "data" );
            var stateObj = dataObj.getJSONObject( "state" );
            var state = new THState(
                    device,
                    dataObj.getBoolean( "online" ),
                    stateObj.getInt( "battery" ),
                    fromCtoF( stateObj.getDouble( "temperature" ) ),
                    stateObj.getDouble( "humidity" ),
                    stateObj.getDouble( "tempCorrection" ),
                    stateObj.getDouble( "humidityCorrection" )
            );
            result.add( state );
        }

        return result;
    }


    private List<Device> getDevices() throws IOException, JSONException {

        //var req = "{\"method\":\"Home.getDeviceList\",\"time\":" + System.currentTimeMillis() + "}";
        var req = "{\"method\":\"Home.getDeviceList\"}";
        var resp = post( "https://api.yosmart.com/open/yolink/v2/api", req, "application/json", true );

        // if the returned object doesn't say successful, throw an exception...
        if( !"Success".equals( resp.optString( "desc" ) ) ) throw new IOException( "YoLink didn't return a device list" );

        // we got a device list, so munge it into something useful...
        var deviceArray = resp.getJSONObject( "data" ).getJSONArray( "devices" );
        var deviceList = new ArrayList<Device>();
        for( final Object _o : deviceArray ) {
            var deviceObj = (JSONObject) _o;
            var device = new Device(
                    deviceObj.getString( "modelName" ),
                    deviceObj.getString( "name" ),
                    deviceObj.getString( "type" ),
                    deviceObj.getString( "deviceId" ),
                    deviceObj.getString( "deviceUDID" ),
                    deviceObj.getString( "token" ) );

            // if it's the hub, skip it...
            if( "YS1603-UC".equals( device.model ) ) continue;

            deviceList.add( device );
        }

        return deviceList;
    }


    private void ensureAccessToken() throws IOException, JSONException {

        // if we've already got a token, and it isn't near expiration, our work is done...
        if( (accessToken != null) && (accessTokenExpires != null) && accessTokenExpires.minus( Duration.ofMinutes( 15 ) ).isAfter( Instant.now() ) )
            return;

        // otherwise, request an access token from YoLink...
        var req = "grant_type=client_credentials&client_id=" + clientID + "&client_secret=" + secret;
        var resp = post( "https://api.yosmart.com/open/yolink/token", req, "application/x-www-form-urlencoded", false );

        // if we got a response, but no access token or expiration, throw an exception...
        if( !resp.has( "access_token" ) ) throw new IOException( "YoLink failed to return an access token" );
        if( !resp.has( "expires_in"   ) ) throw new IOException( "YoLink failed to return an expiration time" );

        // all is good; update our access token...
        accessToken = resp.getString( "access_token" );
        accessTokenExpires = Instant.now().plus( Duration.ofSeconds( resp.getInt( "expires_in" ) ) );
    }


    private JSONObject post( final String _url, final String _request, final String _contentType, final boolean _authorize ) throws IOException, JSONException {

        URL url = new URL( _url );
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod( "POST" );
        con.setRequestProperty( "Accept", "application/json" );
        con.setRequestProperty( "Content-Type", _contentType );

        if( _authorize )
            con.setRequestProperty( "Authorization", "Bearer " + accessToken );

        con.setDoOutput( true );

        try( OutputStream os = con.getOutputStream() ) {
            byte[] input = _request.getBytes( StandardCharsets.UTF_8 );
            os.write( input, 0, input.length );
        }

        try( BufferedReader br = new BufferedReader( new InputStreamReader( con.getInputStream(), StandardCharsets.UTF_8 ) ) ) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            return new JSONObject( response.toString() );
        }
    }


    private record Device( String model, String name, String type, String id, String udid, String token ){}

    private record THState( Device device, boolean online, int battery, double temperature, double humidity, double tempCorrection, double humidityCorrection ){}


    /**
     * This stub main is here for troubleshooting only - using it you can run the monitor just once, from a development machine.
     */
    @SuppressWarnings( "DuplicatedCode" )
    public static void main( final String[] _args ) throws InterruptedException {

        // set the configuration file location (must do before any logging actions occur)...
        System.getProperties().setProperty( "java.util.logging.config.file", "logging.properties" );
        //noinspection unused
        Logger LOGGER = getLogger();

        Config config = new Config();
        Outcome<?> result = config.init( "MonitorConfigurator", "configuration.java", Files.readToString( new File( "credentials.txt" ) ) );
        if( result.notOk() ) throw new IllegalArgumentException( "bad configuration" );
        PostOffice po = new PostOffice( config.postOfficeConfig );
        Mailbox mailbox = po.createMailbox( "monitor" );

        var mi = config.monitors.get( 0 );
        var mon = new YoLink( mailbox, mi.parameters() );

        mon.run();

        sleep( 5000 );

        //noinspection ResultOfMethodCallIgnored
        config.hashCode();
    }
}
