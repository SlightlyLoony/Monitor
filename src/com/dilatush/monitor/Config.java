package com.dilatush.emailservice;


import com.dilatush.mop.PostOffice;
import com.dilatush.util.config.AConfig;

import java.util.List;

/**
 * Configuration POJO for the EmailService application.
 *
 * @author Tom Dilatush  tom@dilatush.com
 */
public class Config extends AConfig {

    // our post office configuration...
    public PostOffice.PostOfficeConfig postOfficeConfig;

    // our controller configuration...
    public ControllerConfig controllerConfig;


    /**
     * Verify the fields of this configuration.
     */
    @Override
    public void verify( final List<String> _messages ) {
        verifySubConfig( postOfficeConfig, _messages, "postOfficeConfig" );
        verifySubConfig( controllerConfig, _messages, "controllerConfig" );
    }
}
