package localiser;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import hk.ust.cse.com107x.indoor.R;
import localiser.algorithms.AbstractLocaliserAlgorithm;
import localiser.database.FingerprintDatabase;
import localiser.units.Fingerprint;
import localiser.database.POIDatabase;
import localiser.units.Coordinates;
import localiser.units.PointOfInterest;
import localiser.units.Tuple;

import static junit.framework.Assert.assertNotNull;

/**
 * Created by sebastian on 26/12/15.
 */
public class LocaliserController extends BroadcastReceiver
{

    public class NoWIFIException extends Exception{
        public NoWIFIException(String exc){
            super(exc);
        }
    };

    public interface Callback{
        public void locationUpdated(Coordinates c);
    }


    private final AbstractLocaliserAlgorithm algorithm;
    private final Set<Callback> callbacks = new HashSet<>();
    private final FingerprintDatabase db_finger;
    private final POIDatabase db_poi;
    protected final WifiManager wifiManager;
    protected final Context c;



    private Coordinates lastCoordinates;

    public LocaliserController(AbstractLocaliserAlgorithm algorithm, Context c) throws NoWIFIException, IOException {

        this.db_finger = new FingerprintDatabase(c, R.raw.k123);
        this.db_poi = new POIDatabase(c);
        this.c = c;
        this.algorithm = algorithm;

        this.wifiManager = (WifiManager) c.getSystemService(Context.WIFI_SERVICE);

        //check if WIFI is enabled and whether scanning launched
        if(!wifiManager.isWifiEnabled() || !wifiManager.startScan())
        {
            throw new NoWIFIException("WIFI is not enabled");
        }

    }

    public void registerForLocationUpdates(final Callback callback)
    {

        this.callbacks.add(callback);
        if(this.callbacks.size()==1)
            c.registerReceiver(this,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

    }
    public void unregisterForLocationUpdates(final Callback callback){
        this.callbacks.remove(callback);
        if(this.callbacks.size()==0)
            c.unregisterReceiver(this);
    }

    private void locationUpdated(Coordinates c) {
        //get new location updates
        for (Callback ca : this.callbacks) {
            ca.locationUpdated(c);
        }
    }

    public List<Tuple<Double,PointOfInterest>> getClosestPOI(Coordinates co)
    {

        if(co == null)
        {
            co = lastCoordinates;
        }


        List<Tuple<Double,PointOfInterest>> closest = new LinkedList<>();
        if(co == null)
        {
            return closest;
        }
        for(PointOfInterest poi: db_poi)
        {
            closest.add(new Tuple<Double, PointOfInterest>(co.distance(poi.coordinates), poi));
        }

        //sort by distance
        Collections.sort(closest, new Comparator<Tuple<Double, PointOfInterest>>() {
            @Override
            public int compare(Tuple<Double, PointOfInterest> lhs, Tuple<Double, PointOfInterest> rhs) {
                return lhs.first.compareTo(rhs.first);
            }
        });

        return closest;
    }
    public List<Tuple<Double,PointOfInterest>> getClosestPOI(Coordinates c, int num)
    {
        List<Tuple<Double,PointOfInterest>> closest = this.getClosestPOI(c);

        //trim N
        if(closest.size()>num)
            return closest.subList(0,num);
        else
            return closest;
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        List<ScanResult> result = wifiManager.getScanResults();
        Coordinates c = this.algorithm.getLocation(Fingerprint.fromScanResult(result), db_finger);
        if(c!=null){
            this.locationUpdated(c);
            lastCoordinates = c;
        }

        wifiManager.startScan();
    }

    public Coordinates getLastCoordinates() {
        return lastCoordinates;
    }



}
