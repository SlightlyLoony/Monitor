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

        // NTPServer configuration...
        Map<String,Object> params = new HashMap<>();
        params.put( "URL",      "http://ntpserver.dilatush.com" );
        params.put( "username", "admin"                         );
        params.put( "password", === NTP server password ===     );
        config.monitors.add( new MonitorInstance( NTPServer.class, params, Duration.ofMinutes( 1 ) ) );

        // YoLink configuration...
        var triggers = new ArrayList<YoLinkTriggerDef>();
        triggers.add( new YoLinkTriggerDef( "Office", 100, YoLinkTriggerType.BELOW, YoLinkTriggerField.TEMPERATURE, YoLinkTriggerClass.VALUE,
                "YoLink.below50", "Office too cold", "Office temperature is too low: %.1f °F.", 8, null ) );
        params = new HashMap<>();
        params.put( "clientID", === YoLink client ID === );
        params.put( "secret"  , === YoLink secret ===    );
        params.put( "triggers", triggers                 );
        config.monitors.add( new MonitorInstance( YoLink.class, params, Duration.ofMinutes( 15 ) ) );
    }
}
