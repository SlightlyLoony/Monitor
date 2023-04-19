import com.dilatush.monitor.Config;
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
        config.ntpServerURL      = "http://ntpserver.dilatush.com";
        config.ntpServerUsername = "admin";
        config.ntpServerPassword = === NTP server password ===;
        config.ntpServerInterval = Duration.ofHours( 1 );
    }
}
