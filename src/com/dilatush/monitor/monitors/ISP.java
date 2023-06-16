package com.dilatush.monitor.monitors;

import com.dilatush.util.HTTP;
import com.dilatush.util.Outcome;
import com.dilatush.util.Strings;
import com.dilatush.util.ip.IPAddress;

import java.util.HashMap;
import java.util.Map;

public class ISP /* extends AMonitor */ {


    private static final Outcome.Forge<IPAddress> FORGE_IP       = new Outcome.Forge<>();
    private static final Outcome.Forge<ISPInfo>   FORGE_ISP_INFO = new Outcome.Forge<>();

    private final Map<IPAddress,ISPInfo> isps = new HashMap<>();

//    /**
//     * Creates a new instance of this class with the given Mailbox.
//     *
//     * @param _mailbox  The mailbox for this monitor to use.
//     * @param _interval the interval between runs for this monitor.
//     */
//    public ISP( final Mailbox _mailbox, final Duration _interval ) {
//
//        super( _mailbox, _interval );
//    }
//
//
//    /**
//     * Perform the periodic monitoring.
//     */
//    @Override
//    public void run() {
//
//    }


    /**
     * Return the public IP address for requests made from this machine.  This is the IP address provided by the ISP.  It may change from time to time, and it
     * <i>will</i> change if the ISP changes.
     *
     * @return The outcome of this operation.  If successful, the outcome will be ok, and will contain the public IP address.  If unsuccessful, the outcome will
     * contain an explanatory message and possible the exception that caused the problem.
     */
    private Outcome<IPAddress> getPublicIP() {

        var reqOutcome = HTTP.requestText( "http://checkip.amazonaws.com" );
        if( reqOutcome.notOk() ) return FORGE_IP.notOk( reqOutcome );
        return IPAddress.fromString( Strings.stripTrailingNewlines( reqOutcome.info() ) );
    }


    private Outcome<ISPInfo> getISPInfo( final IPAddress _ip ) {

        synchronized( isps ) {

            // have we ever seen this IP before?
            if( isps.containsKey( _ip ) ) {

                // yes, so return the ISP info we already figured out...
                return FORGE_ISP_INFO.ok( isps.get( _ip ) );
            }

            // no, so query for it...
            var reqOutcome = HTTP.requestJSONText( "http://rdap.arin.net/registry/ip/" + _ip.toString() );
            if( reqOutcome.notOk() ) return FORGE_ISP_INFO.notOk( reqOutcome );
            var response = reqOutcome.info().toLowerCase();

            // make our ISP record...
            var isp = (response.contains( "starlink" ) || response.contains( "spacex" )) ?
                new ISPInfo( _ip, ISPRank.PRIMARY,   "Starlink" ) :
                new ISPInfo( _ip, ISPRank.SECONDARY, "Verizon"  );

            // update our cache...
            isps.put( _ip, isp );

            // return the answer...
            return FORGE_ISP_INFO.ok( isp );
        }
    }


    public record ISPInfo( IPAddress ip, ISPRank rank, String name ){}

    public enum ISPRank { PRIMARY, SECONDARY }


    public static void main( String[] args ) {

        var isp = new ISP();
        var ipOut = isp.getPublicIP();
        if( ipOut.ok() ) {
            var ispOut = isp.getISPInfo( ipOut.info() );
            ispOut = isp.getISPInfo( ipOut.info() );
            isp.hashCode();
        }
    }
}
