package com.dilatush.monitor;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.PostOffice;
import com.dilatush.util.Files;
import com.dilatush.util.Outcome;
import com.dilatush.util.ScheduledExecutor;

import java.io.File;
import java.time.Duration;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;

public class Monitor {

    public static void main( String[] args ) throws InterruptedException {

        // set the configuration file location (must do before any logging actions occur)...
        System.getProperties().setProperty( "java.util.logging.config.file", "logging.properties" );
        Logger LOGGER = getLogger();


        LOGGER.info( "Monitor is starting..." );

        // set up our scheduled executor...
        var executorThreads = 2;
        var executorDaemon = false;  // false means user threads...
        ScheduledExecutor executor = new ScheduledExecutor( executorThreads, executorDaemon );

        // get our configuration...
        Config config = new Config();
        Outcome<?> result = config.init( "MonitorConfigurator", "configuration.java", Files.readToString( new File( "credentials.txt" ) ) );

        // if our configuration is not valid, just get out of here...
        if( !result.ok() ) {
            LOGGER.severe( "Aborting; configuration is invalid\n" + result.msg() );
            System.exit( 1 );
        }

        // if we make it here, then we have a validated configuration, and we should be good to go!

        // start up our post office...
        PostOffice po = new PostOffice( config.postOfficeConfig );
        Mailbox mailbox = po.getMailbox( "monitor" );

        // launch all our monitors...

        // we can just leave, because the executor threads are user threads...
    }
}
