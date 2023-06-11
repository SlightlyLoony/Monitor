package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.General.isNotNull;

/**
 * Implements a simple monitor for the health of a JVM.
 *
 * @author Tom Dilatush  tom@dilatush.com
 */
public class JVM extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private final String name;

    private long usedBytes;            // memory allocated and actually being used, both code and data...
    private long freeBytes;            // memory allocated by not currently in use...
    private long allocatedBytes;       // memory allocated (sum of used and free memory)...
    private long availableBytes;       // memory that is currently unallocated, but which the JVM will try to allocate if needed...
    private long maxBytes;             // memory that the JVM could use (sum of allocated and available)...
    private int  cpus;                 // number of CPUs available to the JVM...
    private int  totalThreads;         // total number of active threads in the JVM...
    private int  newThreads;           // new threads that have not yet started...
    private int  runningThreads;       // running threads...
    private int  blockedThreads;       // threads blocked on a monitor...
    private int  waitingThreads;       // threads waiting for another thread...
    private int  timedWaitingThreads;  // threads waiting for another thread, up to a certain time...
    private int  terminatedThreads;    // threads that have terminated...


    /**
     * Creates a new instance of this class with the given Mailbox.
     *
     * @param _mailbox  The mailbox for this monitor to use.
     * @param _params The parameters for this monitor.
     * @param _interval the interval between runs for this monitor.
     */
    public JVM( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );
        name = (String) _params.get( "name" );
    }


    /**
     * Runs this monitor and fills the specified message with the results.
     */
    public void run() {

        // first run the monitor...
        capture();

        Message msg = mailbox.createPublishMessage( name + "_jvm.monitor" );

        // send the message interval...
        msg.putDotted( "monitor.jvm.messageIntervalMs",     interval.toMillis()        );

        // then fill in all the results...
        msg.putDotted( "monitor.jvm.usedBytes",            usedBytes           );
        msg.putDotted( "monitor.jvm.freeBytes",            freeBytes           );
        msg.putDotted( "monitor.jvm.allocatedBytes",       allocatedBytes      );
        msg.putDotted( "monitor.jvm.availableBytes",       availableBytes      );
        msg.putDotted( "monitor.jvm.maxBytes",             maxBytes            );
        msg.putDotted( "monitor.jvm.cpus",                 cpus                );
        msg.putDotted( "monitor.jvm.totalThreads",         totalThreads        );
        msg.putDotted( "monitor.jvm.newThreads",           newThreads          );
        msg.putDotted( "monitor.jvm.runningThreads",       runningThreads      );
        msg.putDotted( "monitor.jvm.blockedThreads",       blockedThreads      );
        msg.putDotted( "monitor.jvm.waitingThreads",       waitingThreads      );
        msg.putDotted( "monitor.jvm.timedWaitingThreads",  timedWaitingThreads );
        msg.putDotted( "monitor.jvm.terminatedThreads",    terminatedThreads   );

        // send it!
        mailbox.send( msg );
        LOGGER.info( "Sent JVM monitor message" );
    }


    private void capture() {

        // first we investigate memory, running the gc first to eliminate dead objects...
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        long free = runtime.freeMemory();
        long max = runtime.maxMemory();
        long total = runtime.totalMemory();

        usedBytes      = total - free;
        freeBytes      = free;
        allocatedBytes = total;
        availableBytes = max - total;
        maxBytes       = max;

        // now we see how many CPUs we can use...
        cpus = runtime.availableProcessors();

        // now we see how many threads we're running and what state they're in...
        totalThreads = newThreads = runningThreads = blockedThreads = waitingThreads = timedWaitingThreads = terminatedThreads = 0;
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        while( isNotNull( group.getParent() ) )
            group = group.getParent();
        Thread[] threads = new Thread[ group.activeCount() * 4 / 3 ];
        int count = group.enumerate( threads );
        for( int i =  0; i < count; i++ ) {
            Thread t = threads[i];
            totalThreads++;
            switch( t.getState() ) {
                case NEW -> newThreads++;
                case BLOCKED -> blockedThreads++;
                case WAITING -> waitingThreads++;
                case RUNNABLE -> runningThreads++;
                case TERMINATED -> terminatedThreads++;
                case TIMED_WAITING -> timedWaitingThreads++;
            }
        }
    }
}
