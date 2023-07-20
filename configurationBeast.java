import com.dilatush.monitor.Config;
import com.dilatush.monitor.MonitorInstance;
import com.dilatush.monitor.monitors.NTPServer;
import com.dilatush.monitor.monitors.ISP;
import com.dilatush.monitor.monitors.OS;
import com.dilatush.monitor.monitors.JVM;
import com.dilatush.monitor.monitors.JVMs;
import com.dilatush.monitor.monitors.LAN;
import com.dilatush.monitor.monitors.LAN.Check;
import com.dilatush.monitor.monitors.yolink.YoLink;
import com.dilatush.monitor.monitors.yolink.YoLinkTriggerDef;
import com.dilatush.monitor.monitors.yolink.YoLinkTriggerField;
import com.dilatush.monitor.monitors.yolink.YoLinkTriggerType;
import com.dilatush.monitor.monitors.yolink.YoLinkTriggerClass;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.config.Configurator;
import com.dilatush.util.config.AConfig;
import com.dilatush.util.ip.IPv4Address;
import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.time.Duration;
import java.util.function.Function;

/**
 * Configurator for Monitor running on Beast.
 */

public class MonitorConfigurator implements Configurator {

    public void config( final AConfig _config ) {

        // configure our post office...
        var poc     = new PostOffice.PostOfficeConfig();
        poc.name    = === beast MOP name ===;
        poc.secret  = === beast MOP secret ===;
        poc.cpoHost = "cpo.dilatush.com";


        // set up our configuration object...
        Config config           = (Config) _config;
        config.postOfficeConfig = poc;

        // configure our host...
        config.host = "beast";

        //////////// Monitors Configuration //////////////

        Map<String,Object> params;

        // YoLink configuration...
        var triggers = new ArrayList<YoLinkTriggerDef>();
        triggers.add( new YoLinkTriggerDef(
                "Office", 50, YoLinkTriggerType.BELOW, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.tooCold", "Office too cold: %1$.1f°F.", "Office temperature is below %2$.1f°F: %1$.1f°F.", 8, Duration.ofHours( 1 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "Deck", 32, YoLinkTriggerType.BELOW, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.TRANSITION,
                "YoLink.frost", "Frost warning: %1$.1f°F.", "Outside (deck) temperature went below freezing: %1$.1f°F.", 6, null ) );
        triggers.add( new YoLinkTriggerDef(
                "Kitchen Freezer", 20, YoLinkTriggerType.ABOVE, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.freezerOverTemp", "%4$s too warm: %1$.1f°F.", "%4$s temperature above %2$.1f°F: %1$.1f°F.", 8, Duration.ofMinutes( 30 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "Garage Freezer,Shed Freezer", 10, YoLinkTriggerType.ABOVE, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.freezerOverTemp", "%4$s too warm: %1$.1f°F.", "%4$s temperature above %2$.1f°F: %1$.1f°F.", 8, null ) );
        triggers.add( new YoLinkTriggerDef(
                "Kitchen Refrigerator", 40, YoLinkTriggerType.ABOVE, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.refrigeratorOverTemp", "%4$s too warm: %1$.1f°F.", "%4$s temperature above %2$.1f°F: %1$.1f°F.", 8, Duration.ofMinutes( 30 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "Office", 90, YoLinkTriggerType.ABOVE, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.tooWarm", "Office too warm: %1$.1f°F.", "Office temperature is above %3$.1f°F: %1$.1f°F.", 8, Duration.ofHours( 1 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "?", 0D, YoLinkTriggerType.EQUAL, YoLinkTriggerField.ONLINE, YoLinkTriggerClass.VALUE,
                "YoLink.offline", "%4$s offline.", "YoLink sensor %4$s is offline.", 8, Duration.ofHours( 2 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "?", 2.5D, YoLinkTriggerType.BELOW, YoLinkTriggerField.BATTERY, YoLinkTriggerClass.VALUE,
                "YoLink.lowBattery", "%4$s battery low.", "YoLink sensor %4$s battery is low: %1$.0f of 4.", 5, Duration.ofHours( 24 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "?", 1.5D, YoLinkTriggerType.BELOW, YoLinkTriggerField.BATTERY, YoLinkTriggerClass.VALUE,
                "YoLink.criticalLowBattery", "%4$s battery critically low.", "YoLink sensor %4$s battery is critically low.", 8, Duration.ofHours( 3 ) ) );
        params = new HashMap<>();
        params.put( "clientID", === YoLink client ID === );
        params.put( "secret"  , === YoLink secret ===    );
        params.put( "triggers", triggers                 );
        config.monitors.add( new MonitorInstance( YoLink.class, params, Duration.ofMinutes( 15 ) ) );

        // NTPServer configuration...
        params = new HashMap<>();
        params.put( "URL",      "http://ntpserver.dilatush.com" );
        params.put( "username", "admin"                         );
        params.put( "password", === NTP server password ===     );
        config.monitors.add( new MonitorInstance( NTPServer.class, params, Duration.ofMinutes( 1 ) ) );

        // OS configuration...
        params = new HashMap<>();
        params.put( "name", "beast" );
        config.monitors.add( new MonitorInstance( OS.class, params, Duration.ofMinutes( 10 ) ) );

        // JVM configuration...
        params = new HashMap<>();
        params.put( "name", "beast_monitor" );
        config.monitors.add( new MonitorInstance( JVM.class, params, Duration.ofMinutes( 10 ) ) );

        // JVMs configuration...
        params = new HashMap<>();
        params.put( "JVMs",
                "WeatherCapture:Weather Capture service,Weather:Weather Processing service,WWW:Web service,Monitor:Monitoring service," +
                "Events:Events service,CPO:Central Post Office service,ace:Ubiquiti Unifi service");
        config.monitors.add( new MonitorInstance( JVMs.class, params, Duration.ofMinutes( 60 ) ) );

        // ISP configuration...
        params = new HashMap<>();
        config.monitors.add( new MonitorInstance( ISP.class, params, Duration.ofSeconds( 15 ) ) );

        // LAN configuration...
        var checks = new ArrayList<Check>();
        checks.add( new Check( "barnswitch",  IPv4Address.fromString( "10.2.4.254"   ).info(), 80,  50 ) );
        checks.add( new Check( "barnrouter",  IPv4Address.fromString( "10.2.4.1"     ).info(), 80,  50 ) );
        checks.add( new Check( "barnradio",   IPv4Address.fromString( "10.1.100.101" ).info(), 80, 250 ) );
        checks.add( new Check( "barnnano",    IPv4Address.fromString( "10.2.100.2"   ).info(), 80, 250 ) );
        checks.add( new Check( "houseradio",  IPv4Address.fromString( "10.1.100.100" ).info(), 80, 250 ) );
        checks.add( new Check( "houserouter", IPv4Address.fromString( "10.1.4.1"     ).info(), 80,  50 ) );
        checks.add( new Check( "houseswitch", IPv4Address.fromString( "10.1.4.254"   ).info(), 80,  50 ) );
        checks.add( new Check( "shednano",    IPv4Address.fromString( "10.2.100.3"   ).info(), 80, 250 ) );
        checks.add( new Check( "shedrouter",  IPv4Address.fromString( "10.2.100.4"   ).info(), 80,  50 ) );
        checks.add( new Check( "failtest",    IPv4Address.fromString( "10.2.100.4"   ).info(), 81,  50 ) );
        params = new HashMap<>();
        params.put( "checks", checks );
        config.monitors.add( new MonitorInstance( LAN.class, params, Duration.ofMinutes( 15 ) ) );
    }
}
