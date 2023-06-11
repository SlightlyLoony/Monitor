package com.dilatush.monitor.monitors;

import com.dilatush.mop.Mailbox;
import com.dilatush.mop.Message;
import com.dilatush.util.Executor;

import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.dilatush.mop.util.OS.LINUX;
import static com.dilatush.mop.util.OS.OSX;
import static com.dilatush.util.General.getLogger;
import static com.dilatush.util.Strings.isEmpty;
import static java.lang.Thread.sleep;

/**
 * Implements some simple operating system monitoring for Linux and OSX.
 *
 * @author Tom Dilatush  tom@dilatush.com
 */
public class OS extends AMonitor {

    private static final Logger LOGGER = getLogger();

    private static final long GIGA = 1_000_000_000;  // decimal conventional for disks...
    private static final long KILO = 1_024;
    private static final long MEGA = KILO * KILO;

    private static final Executor osInfoEx         = new Executor( "uname -mnrs"       );
    private static final Executor osxMemInfoEx1    = new Executor( "sysctl hw.memsize" );
    private static final Executor osxMemInfoEx2    = new Executor( "vm_stat"           );
    private static final Executor linuxMemInfoEx   = new Executor( "free -b"           );
    private static final Executor osxCPUInfoEx     = new Executor( "iostat -C"         );
    private static final Executor linuxCPUInfoEx   = new Executor( "cat /proc/stat"    );
    private static final Executor osxDiskInfoEx    = new Executor( "df -lk"            );
    private static final Executor linuxDiskInfoEx1 = new Executor( "df -l"             );
    private static final Executor linuxDiskInfoEx2 = new Executor( "df -li"             );

    private static final Pattern osInfoPat
            = Pattern.compile( "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+" );
    private static final Pattern osxMemInfoPat
            = Pattern.compile( ".*\\.memsize:\\s+(\\d+).*page size of (\\d+).* active:\\s+(\\d+).* wired down:\\s++(\\d+).*", Pattern.DOTALL );
    private static final Pattern linuxMemInfoPat
            = Pattern.compile( ".*Mem:\\s+(\\d+)\\s+(\\d+)\\s+\\d+\\s+\\d+\\s+\\d+.*Swap:\\s+(\\d+)\\s+(\\d+).*", Pattern.DOTALL );
    private static final Pattern osxCPUInfoPat
            = Pattern.compile( ".*(?:([\\d.]+)\\s+){6}([\\d.]+)\\s+.*", Pattern.DOTALL );
    private static final Pattern linuxCPUInfoPat
            = Pattern.compile( "cpu\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+.*", Pattern.DOTALL );
    private static final Pattern osxDiskInfoPat
            = Pattern.compile( ".* (\\d+) +(\\d+) +(\\d+) +(\\d+)% +(\\d+) +(\\d+) +(\\d+)% +/System/Volumes/Data$.*", Pattern.DOTALL | Pattern.MULTILINE );
    private static final Pattern linuxDiskInfoPat1
            = Pattern.compile( ".* (\\d+) +(\\d+) +(\\d+) +(\\d+)% +/$.*", Pattern.DOTALL | Pattern.MULTILINE );
    private static final Pattern linuxDiskInfoPat2
            = Pattern.compile( ".* (\\d+) +(\\d+) +(\\d+) +(\\d+)% +/$.*", Pattern.DOTALL | Pattern.MULTILINE );

    private final String name;

    private boolean  valid;
    private com.dilatush.mop.util.OS os;
    private String   kernelName;
    private String   hostName;
    private String   kernelVersion;
    private String   architecture;
    private String   errorMessage;
    private long     totalMemory;  // in megabytes...
    private long     usedMemory;  // in megabytes...
    private long     freeMemory;  // in megabytes...
    private float    freeMemoryPct;
    private long     totalDisk;  // in gigabytes...
    private long     usedDisk;  // in gigabytes...
    private long     freeDisk;  // in gigabytes...
    private float    freeDiskPct;
    private long     totalINodes;
    private long     usedINodes;
    private long     freeINodes;
    private float    freeINodesPct;
    private float    cpuBusyPct;
    private float    cpuIdlePct;


    /**
     * Create a new instance of this class to monitor a TF-1006-PRO NTP server at the given URL, with the given username and password (contained in the parameters).
     *
     * @param _mailbox The mailbox for this monitor to use.
     * @param _params The parameters for this monitor.
     * @param _interval the interval between runs for this monitor.
     */
    public OS( final Mailbox _mailbox, final Map<String,Object> _params, final Duration _interval ) {
        super( _mailbox, _interval );
        name = (String) _params.get( "name" );
    }


    /**
     * Perform the periodic monitoring.  For this monitor that interval should be 10 minutes.
     */
    @Override
    public void run() {

        // first run the monitor...
        capture();

        Message msg = mailbox.createPublishMessage( name + "_os.monitor" );

        // send the message interval...
        msg.putDotted( "monitor.os.messageIntervalMs",     interval.toMillis()        );

        // if the results were not valid, log an error message and leave...
        if( !valid ) {
            LOGGER.warning( "Error reading OS status: " + errorMessage );
            return;
        }

        // otherwise, fill in everything we've learned...
        msg.putDotted( "monitor.os.os",            (os == LINUX) ? "Linux" : "OSX" );
        msg.putDotted( "monitor.os.hostName",      hostName                        );
        msg.putDotted( "monitor.os.kernelName",    kernelName                      );
        msg.putDotted( "monitor.os.kernelVersion", kernelVersion                   );
        msg.putDotted( "monitor.os.architecture",  architecture                    );
        msg.putDotted( "monitor.os.totalMemory",   totalMemory                     );
        msg.putDotted( "monitor.os.usedMemory",    usedMemory                      );
        msg.putDotted( "monitor.os.freeMemory",    freeMemory                      );
        msg.putDotted( "monitor.os.freeMemoryPct", freeMemoryPct                   );
        msg.putDotted( "monitor.os.cpuBusyPct",    cpuBusyPct                      );
        msg.putDotted( "monitor.os.cpuIdlePct",    cpuIdlePct                      );
        msg.putDotted( "monitor.os.totalDisk",     totalDisk                       );
        msg.putDotted( "monitor.os.usedDisk",      usedDisk                        );
        msg.putDotted( "monitor.os.freeDisk",      freeDisk                        );
        msg.putDotted( "monitor.os.freeDiskPct",   freeDiskPct                     );
        msg.putDotted( "monitor.os.totalINodes",   totalINodes                     );
        msg.putDotted( "monitor.os.usedINodes",    usedINodes                      );
        msg.putDotted( "monitor.os.freeINodes",    freeINodes                      );
        msg.putDotted( "monitor.os.freeInodesPct", freeINodesPct                   );

        // send it!
        mailbox.send( msg );
    }


    /**
     * Runs this monitor, executing operating system commands to find its current state.  The results are used in the {@link #run()} method.
     */
    private void capture() {

        // mark results invalid until we've successfully completed...
        valid = false;
        errorMessage = null;

        // first find out what kind of OS we're running on...
        String result = osInfoEx.run();
        if( isEmpty( result ) ) {
            errorMessage = "Command uname failed";
            return;
        }
        Matcher mat = osInfoPat.matcher( result );
        if( mat.matches() ) {
            kernelName = mat.group( 1 );
            hostName = mat.group( 2 );
            kernelVersion = mat.group( 3 );
            architecture = mat.group( 4 ) ;
            if( "Linux".equals( kernelName ) )       os = LINUX;
            else if( "Darwin".equals( kernelName ) ) os = OSX;
            else {
                errorMessage = "Unrecognized kernel name: " + kernelName;
                return;
            }
        }
        else {
            errorMessage = "Unrecognized uname output: " + result;
            return;
        }

        // then do the rest of our investigation in an OS-dependent way...
        if( os == OSX )        runOSX();
        else /* os == LINUX */ runLinux();
    }


    /**
     * Runs this monitor on an OSX system, executing operating system commands to find its current state.  The results are used in the {@link #run()} method.
     */
    private void runOSX() {

        // find our total memory, free, and used...
        String result = osxMemInfoEx1.run() + osxMemInfoEx2.run();
        if( isEmpty( result ) ) {
            errorMessage = "Command sysctl or vm_stat failed";
            return;
        }
        Matcher mat = osxMemInfoPat.matcher( result );
        if( mat.matches() ) {
            int pageSize = Integer.parseInt( mat.group( 2 ) );
            long active = pageSize * Long.parseLong( mat.group( 3 ) );
            long wired = pageSize * Long.parseLong( mat.group( 4 ) );
            totalMemory = (Long.parseLong( mat.group( 1 ) ) + (MEGA >>> 1) ) / MEGA;
            usedMemory = (active + wired + (MEGA >>> 1)) / MEGA;
            freeMemory = totalMemory - usedMemory;
            freeMemoryPct = 100F * freeMemory / totalMemory;
        }
        else {
            errorMessage = "Unrecognized sysctl or vm_stat output: " + result;
            return;
        }

        // find our cpu busy and idle percent (for OSX, it's the last minute average)...
        result = osxCPUInfoEx.run();
        if( isEmpty( result ) ) {
            errorMessage = "Command iostat failed";
            return;
        }
        mat = osxCPUInfoPat.matcher( result );
        if( mat.matches() ) {
            cpuBusyPct = Float.parseFloat( mat.group( 1 ) );
            cpuIdlePct = 100.0f - cpuBusyPct;
        }
        else {
            errorMessage = "Unrecognized iostat output: " + result;
            return;
        }

        // find our disk size and usage...
        result = osxDiskInfoEx.run();
        if( isEmpty( result ) ) {
            errorMessage = "df -lk failed";
            return;
        }
        mat = osxDiskInfoPat.matcher( result );
        if( mat.matches() ) {
            totalDisk     = (Long.parseLong( mat.group( 1 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            usedDisk      = (Long.parseLong( mat.group( 2 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            freeDisk      = (Long.parseLong( mat.group( 3 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            freeDiskPct   = 100F * freeDisk / totalDisk;
            usedINodes    = Long.parseLong( mat.group( 5 ) );
            freeINodes    = Long.parseLong( mat.group( 6 ) );
            totalINodes   = usedINodes + freeINodes;
            freeINodesPct = 100F * freeINodes / totalINodes;
        }
        else {
            errorMessage = "Unrecognized df -l output";
            return;
        }

        valid = true;
    }


    /**
     * Runs this monitor on a Linux system, executing operating system commands to find its current state.  The results are used in the {@link #run()} method.
     */
    private void runLinux() {

        // find our total memory, free, and used...
        String result = linuxMemInfoEx.run();
        if( isEmpty( result ) ) {
            errorMessage = "Command free failed";
            return;
        }
        Matcher mat = linuxMemInfoPat.matcher( result );
        if( mat.matches() ) {
            long total = Long.parseLong( mat.group( 1 ) );
            long used = Long.parseLong( mat.group( 2 ) );
            long swap_total = Long.parseLong( mat.group( 3 ) );
            long swap_used = Long.parseLong( mat.group( 4 ) );
            totalMemory = (total + swap_total + (MEGA >>> 1)) / MEGA;
            usedMemory = (used + swap_used + (MEGA >>> 1)) / MEGA;
            freeMemory = totalMemory - usedMemory;
            freeMemoryPct = 100F * freeMemory / totalMemory;
        }
        else {
            errorMessage = "Unrecognized free output: " + result;
            return;
        }

        // find our cpu busy and idle percent (for Linux, we sample it twice, a second apart )...
        long[] start = linuxCPU();
        if( start == null ) return;
        try { sleep( 1000 ); } catch( InterruptedException _e ) { /* do nothing... */ }
        long[] stop = linuxCPU();
        if( stop == null ) return;
        long total = stop[0] - start[0];
        long idle  = stop[1] - start[1];
        cpuIdlePct = 100.0f * idle / total;
        cpuBusyPct = 100.0f - cpuIdlePct;

        // find our disk size and usage...
        result = linuxDiskInfoEx1.run();
        if( isEmpty( result ) ) {
            errorMessage = "df -l failed";
            return;
        }
        mat = linuxDiskInfoPat1.matcher( result );
        if( mat.matches() ) {
            totalDisk     = (Long.parseLong( mat.group( 1 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            usedDisk      = (Long.parseLong( mat.group( 2 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            freeDisk      = (Long.parseLong( mat.group( 3 ) ) * KILO + (GIGA >>> 1)) / GIGA;
            freeDiskPct   = 100F * freeDisk / totalDisk;

            // now get the inodes...
            result = linuxDiskInfoEx2.run();
            if( isEmpty( result ) ) {
                errorMessage = "df -li failed";
                return;
            }
            mat = linuxDiskInfoPat2.matcher( result );
            if( mat.matches() ) {
                usedINodes = Long.parseLong( mat.group( 2 ) );
                freeINodes = Long.parseLong( mat.group( 3 ) );
                totalINodes = Long.parseLong( mat.group( 1 ) );
                freeINodesPct = 100F * freeINodes / totalINodes;
            }
            else {
                errorMessage = "Unrecognized df -li output";
                return;
            }
        }
        else {
            errorMessage = "Unrecognized df -l output";
            return;
        }

        valid = true;
    }


    private long[] linuxCPU() {
        String result = linuxCPUInfoEx.run();
        if( isEmpty( result ) ) {
            errorMessage = "Command cat failed";
            return null;
        }
        Matcher mat = linuxCPUInfoPat.matcher( result );
        if( mat.matches() ) {
            long user    = Long.parseLong( mat.group( 1 ) );
            long nice    = Long.parseLong( mat.group( 2 ) );
            long system  = Long.parseLong( mat.group( 3 ) );
            long idle    = Long.parseLong( mat.group( 4 ) );
            long iowait  = Long.parseLong( mat.group( 5 ) );
            long irq     = Long.parseLong( mat.group( 6 ) );
            long softirq = Long.parseLong( mat.group( 7 ) );
            long[] times = new long[2];
            times[0] = user + nice + system + idle + iowait + irq + softirq;
            times[1] = idle;
            return times;
        }
        else {
            errorMessage = "Unrecognized cat output: " + result;
            return null;
        }
    }
}
