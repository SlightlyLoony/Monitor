import com.dilatush.monitor.Config;
import com.dilatush.monitor.MonitorInstance;
import com.dilatush.monitor.monitors.NTPServer;
import com.dilatush.monitor.monitors.YoLink;
import com.dilatush.monitor.monitors.YoLinkTriggerDef;
import com.dilatush.monitor.monitors.YoLinkTriggerField;
import com.dilatush.monitor.monitors.YoLinkTriggerType;
import com.dilatush.monitor.monitors.YoLinkTriggerClass;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.config.Configurator;
import com.dilatush.util.config.AConfig;
import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.time.Duration;
import java.util.function.Function;


public class MonitorConfigurator implements Configurator {

    public void config( final AConfig _config ) {

        // configure our post office...
        var poc     = new PostOffice.PostOfficeConfig();
        poc.name    = "monitor";
        poc.secret  = === mop secret ===;
        poc.cpoHost = "cpo.dilatush.com";


        // set up our configuration object...
        Config config           = (Config) _config;
        config.postOfficeConfig = poc;

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
                "?", 0.5D, YoLinkTriggerType.BELOW, YoLinkTriggerField.ONLINE, YoLinkTriggerClass.VALUE,
                "YoLink.offline", "%4$s offline.", "YoLink sensor %4$s is offline.", 8, Duration.ofHours( 2 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "?", 2.5D, YoLinkTriggerType.BELOW, YoLinkTriggerField.BATTERY, YoLinkTriggerClass.VALUE,
                "YoLink.lowBattery", "%4$s battery low.", "YoLink sensor %4$s battery is low.", 5, Duration.ofHours( 24 ) ) );
        triggers.add( new YoLinkTriggerDef(
                "?", 1.5D, YoLinkTriggerType.BELOW, YoLinkTriggerField.BATTERY, YoLinkTriggerClass.VALUE,
                "YoLink.criticalLowBattery", "%4$s battery critically low.", "YoLink sensor %4$s battery is critically low.", 8, Duration.ofHours( 3 ) ) );
        params = new HashMap<>();
        params.put( "clientID", === YoLink client ID === );
        params.put( "secret"  , === YoLink secret ===    );
        params.put( "triggers", triggers                 );
        config.monitors.add( new MonitorInstance( YoLink.class, params, Duration.ofMinutes( 1 ) ) );

        // NTPServer configuration...
        params = new HashMap<>();
        params.put( "URL",      "http://ntpserver.dilatush.com" );
        params.put( "username", "admin"                         );
        params.put( "password", === NTP server password ===     );
        config.monitors.add( new MonitorInstance( NTPServer.class, params, Duration.ofMinutes( 1 ) ) );
    }
}
