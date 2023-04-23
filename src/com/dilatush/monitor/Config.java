package com.dilatush.monitor;


import com.dilatush.mop.PostOffice;
import com.dilatush.util.config.AConfig;

import java.time.Duration;
import java.util.List;

/**
 * Configuration POJO for the Monitor application.
 *
 * @author Tom Dilatush  tom@dilatush.com
 */
public class Config extends AConfig {

    // our post office configuration...
    public PostOffice.PostOfficeConfig postOfficeConfig;

    ///////// Monitors configuration /////////

    public String   ntpServerURL;
    public String   ntpServerUsername;
    public String   ntpServerPassword;
    public Duration ntpServerInterval;

    public String   yolinkClientID;
    public String   yolinkSecret;
    public Duration yolinkInterval;


    /**
     * Verify the fields of this configuration.
     */
    @Override
    public void verify( final List<String> _messages ) {
        verifySubConfig( postOfficeConfig, _messages, "postOfficeConfig" );
    }
}
