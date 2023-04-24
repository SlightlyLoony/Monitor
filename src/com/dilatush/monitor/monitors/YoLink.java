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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.dilatush.util.General.isNull;
import static java.lang.Thread.sleep;

public class YoLink extends AMonitor {


    private final String clientID;
    private final String secret;

    private String accessToken;
    private Instant accessTokenExpires;

    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox The mailbox for this monitor to use.
     */
    public YoLink( final Mailbox _mailbox, final Map<String,Object> _params ) {
        super( _mailbox );

        if( isNull( _params ) ) throw new IllegalArgumentException( "_params must be provided" );

        clientID = (String) _params.get( "clientID" );
        secret   = (String) _params.get( "secret"   );

        if( isNull( clientID, secret ) ) throw new IllegalArgumentException( "clientID and secret parameters must be supplied" );
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
            sendEvents( states );
            sendStatus( states );
        }
        catch( Exception _e ) {
            throw new RuntimeException( _e );
        }
    }


    private void sendStatus( final List<THState> _states ) {

    }


    private void sendEvents( final List<THState> _states ) {

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
                    stateObj.getDouble( "temperature" ),
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
    public static void main( final String[] _args ) throws InterruptedException, IOException {

        Config config = new Config();
        Outcome<?> result = config.init( "MonitorConfigurator", "configuration.java", Files.readToString( new File( "credentials.txt" ) ) );
        if( result.notOk() ) throw new IllegalArgumentException( "bad configuration" );
        PostOffice po = new PostOffice( config.postOfficeConfig );
        Mailbox mailbox = po.createMailbox( "monitor" );

        var mi = config.monitors.get( 1 );
        var mon = new YoLink( mailbox, mi.parameters() );

        mon.run();

        sleep( 5000 );

        //noinspection ResultOfMethodCallIgnored
        config.hashCode();
    }
}

/*
{
    "access_token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2ODIyNjcwNjEsImV4cCI6MTY4MjI3NDI2MSwiaXNzIjoidWFfMjkxM0Y4QjY2OTZFNDA1QTlDQTY4RUIzQkM2MUUyRTIiLCJhdWQiOiI2YjM0M2UxMzdkYjQ0OWYxOWQ5NWU2OWUxN2I0MmUzMiIsInN1YiI6IjAxMjE4N2Q5NzdkNDQ3YWFiNTFjODMxMzQ4Mzg1ZWUzIiwic2NvcGUiOlsiYXMvYXUiXX0.s0Jo9My56IFJRklMrrn2kWAy2t9feSjoSmDAf31Hc1s",
    "refresh_token":"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpYXQiOjE2ODIyNjcwNjEsImV4cCI6MTY4NDg1OTA2MSwiaXNzIjoidWFfMjkxM0Y4QjY2OTZFNDA1QTlDQTY4RUIzQkM2MUUyRTIiLCJhdWQiOiI2YjM0M2UxMzdkYjQ0OWYxOWQ5NWU2OWUxN2I0MmUzMiIsInN1YiI6IjAxMjE4N2Q5NzdkNDQ3YWFiNTFjODMxMzQ4Mzg1ZWUzIiwic2NvcGUiOlsiYXMvYXUiXX0.XmyzMj_q0p7r6ezkbnYLEKBEZU1r45H7fgg_aG7m75Y",
    "scope":["create"],
    "token_type":"bearer",
    "expires_in":7200
}


{
    "code":"000000",
    "method":"Home.getDeviceList",
    "data":
    {
        "devices":
        [
            {
                "modelName":"YS8005-UC",
                "parentDeviceId":null,
                "name":"Deck",
                "type":"THSensor",
                "deviceId":"d88b4c0100040fee",
                "deviceUDID":"6fc5306c97c04b01b906e4dd3277d7ce",
                "token":"FF0AFDD14F84C0D8A485CF027A4A9276"
            },
            {
                "modelName":"YS8003-UC",
                "parentDeviceId":null,
                "name":"Garage Freezer",
                "type":"THSensor",
                "deviceId":"d88b4c0200077361",
                "deviceUDID":"e4a7d8d880664b2ab95e832ac6cce5cc",
                "token":"1A5E3A123E8213D1D85348F37E32CD0E"
            },
            {
                "modelName":"YS8003-UC",
                "parentDeviceId":null,
                "name":"Kitchen Freezer",
                "type":"THSensor",
                "deviceId":"d88b4c0200055b4e",
                "deviceUDID":"3f553a1b02f245088196dbff3b32b9d3",
                "token":"10FA82A1632A3A329257E0D614C7B892"
            },
            {
                "modelName":"YS8003-UC",
                "parentDeviceId":null,
                "name":"Kitchen Refrigerator",
                "type":"THSensor",
                "deviceId":"d88b4c0200078e93",
                "deviceUDID":"0475aac67d3e4c43a86d9d109e56c973",
                "token":"6438C0D3A0B1CC10ADD8137CF579EF19"
            },
            {
                "modelName":"YS8003-UC",
                "parentDeviceId":null,
                "name":"Office",
                "type":"THSensor",
                "deviceId":"d88b4c0200078e83",
                "deviceUDID":"7d496571ce2545c0a67c942be5134883",
                "token":"CEC7799956DE54F760FCC68F29B69031"
            },
            {
                "modelName":"YS8003-UC",
                "parentDeviceId":null,
                "name":"Shed Freezer",
                "type":"THSensor",
                "deviceId":"d88b4c020007739f",
                "deviceUDID":"3be18321d79b4c02be5c23ccb3047a49",
                "token":"6A4D304B0B403FEA7EAE9425A7731041"
            },
            {
                "modelName":"YS1603-UC",
                "parentDeviceId":null,
                "name":"YoLink Hub",
                "type":"Hub",
                "deviceId":"d88b4c160301865d",
                "deviceUDID":"e156407b2bf446e8ac81ee2e25a068a0",
                "token":"3ABBF1D82985E793BA8C55683242115B"
            }
        ]
    },
    "msgid":1682276189699,
    "time":1682276189699,
    "desc":"Success"
}
 */
