import com.dilatush.emailservice.Config;
import com.dilatush.emailservice.ControllerConfig;
import com.dilatush.emailservice.EmailProviderConfig;
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


public class EmailServiceConfigurator implements Configurator {

    public void config( final AConfig _config ) {

        // configure our post office...
        var poc     = new PostOffice.PostOfficeConfig();
        poc.name    = "emailservice";
        poc.secret  = === mop secret ===;
        poc.cpoHost = "cpo.dilatush.com";

        // configure our controller (along with the email provider configurations which it contains)...
        var cc = new ControllerConfig();

        // set up our provider order...
        cc.providerOrder = List.of( "gmail", "ses", "elastic" );

        /*************
         * BEGIN email providers' configurations.
         *************/


        // set up our gmail configuration...
        var epc = new EmailProviderConfig();

        // the JavaMail properties...
        // These properties were researched via a large number of web sites.  There are many, many more
        // JavaMail properties not specified here, which may be needed for providers other than gmail.
        epc.addJavaMailProperty( "mail.smtp.user",                   === gmail user ===               );
        epc.addJavaMailProperty( "mail.smtp.password",               === gmail password ===           );
        epc.addJavaMailProperty( "mail.smtp.host",                   "smtp.gmail.com"                 );
        epc.addJavaMailProperty( "mail.smtp.starttls.enable",        true                             );
        epc.addJavaMailProperty( "mail.smtp.starttls.required",      true                             );
        epc.addJavaMailProperty( "mail.smtp.auth",                   true                             );
        epc.addJavaMailProperty( "mail.smtp.port",                   587                              );
        epc.addJavaMailProperty( "mail.smtp.connectiontimeout",      5000                             );
        epc.addJavaMailProperty( "mail.smtp.timeout",                5000                             );
        epc.addJavaMailProperty( "mail.smtp.writetimeout",           5000                             );
        // EmailProvider configuration information...
        epc.name = "gmail";
        epc.from = "Beast<empire@dilatush.com>";
        epc.linger = Duration.ofSeconds( 60 );
        epc.initialSessionWait = Duration.ofSeconds( 30 );
        epc.maxQueueTime = Duration.ofMinutes( 5 );
        epc.nextSessionWait = (prevWait) -> {
            var oldSecs = prevWait.toSeconds();
            var newSecs = oldSecs + oldSecs + ((oldSecs + 1) >>> 1);  // multiply by 2.5...
            return Duration.ofSeconds( newSecs );
        };

        cc.addEmailProviderConfig( epc.name, epc );


        // set up our ses properties...
        epc = new EmailProviderConfig();

        // the JavaMail properties...
        epc.addJavaMailProperty( "mail.smtp.user",                   === ses user ===                     );
        epc.addJavaMailProperty( "mail.smtp.password",               === ses password ===                 );
        epc.addJavaMailProperty( "mail.smtp.host",                   "email-smtp.us-west-1.amazonaws.com" );
        epc.addJavaMailProperty( "mail.smtp.starttls.enable",        true                                 );
        epc.addJavaMailProperty( "mail.smtp.starttls.required",      true                                 );
        epc.addJavaMailProperty( "mail.smtp.auth",                   true                                 );
        epc.addJavaMailProperty( "mail.smtp.port",                   587                                  );
        epc.addJavaMailProperty( "mail.smtp.connectiontimeout",      5000                                 );
        epc.addJavaMailProperty( "mail.smtp.timeout",                5000                                 );
        epc.addJavaMailProperty( "mail.smtp.writetimeout",           5000                                 );

        // EmailProvider configuration information...
        epc.name = "ses";
        epc.from = "Beast<tom.dilatush@gmail.com>";
        epc.linger = Duration.ofSeconds( 60 );
        epc.initialSessionWait = Duration.ofSeconds( 10 );
        epc.maxQueueTime = Duration.ofMinutes( 5 );
        epc.nextSessionWait = (prevWait) -> {
            var oldSecs = prevWait.toSeconds();
            var newSecs = oldSecs + oldSecs;  // multiply by 2...
            return Duration.ofSeconds( newSecs );
        };

        cc.addEmailProviderConfig( epc.name, epc );


        // set up our elasticemail.com properties...
        epc = new EmailProviderConfig();

        // the JavaMail properties...
        epc.addJavaMailProperty( "mail.smtp.user",                   === elasticemail user ===      );
        epc.addJavaMailProperty( "mail.smtp.password",               === elasticemail password ===  );
        epc.addJavaMailProperty( "mail.smtp.host",                   "smtp.elasticemail.com"        );
        epc.addJavaMailProperty( "mail.smtp.starttls.enable",        true                           );
        epc.addJavaMailProperty( "mail.smtp.starttls.required",      true                           );
        epc.addJavaMailProperty( "mail.smtp.auth",                   true                           );
        epc.addJavaMailProperty( "mail.smtp.port",                   2525                           );
        epc.addJavaMailProperty( "mail.smtp.connectiontimeout",      5000                           );
        epc.addJavaMailProperty( "mail.smtp.timeout",                5000                           );
        epc.addJavaMailProperty( "mail.smtp.writetimeout",           5000                           );

        // EmailProvider configuration information...
        epc.name = "elastic";
        epc.from = "Beast<tom.dilatush@gmail.com>";
        epc.linger = Duration.ofSeconds( 10 );
        epc.initialSessionWait = Duration.ofSeconds( 10 );
        epc.maxQueueTime = Duration.ofMinutes( 5 );
        epc.nextSessionWait = (prevWait) -> {
            var oldSecs = prevWait.toSeconds();
            var newSecs = oldSecs + oldSecs;  // multiply by 2...
            return Duration.ofSeconds( newSecs );
        };

        cc.addEmailProviderConfig( epc.name, epc );

        /*************
         * END email providers' configurations.
         *************/


        // set up our configuration object...
        Config config           = (Config) _config;
        config.postOfficeConfig = poc;
        config.controllerConfig = cc;
    }
}
