package com.gmail.manhle240406.project.adoption;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.gmail.manhle240406.project.R;

/** Retrieves a real device location and reports honestly when no nearby data provider exists. */
public final class NearbyMapActivity extends AppCompatActivity {
    private TextView status; private View scan; private LocationManager locations; private CancellationSignal cancellation;
    private final android.os.Handler timeout=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable timedOut=()->finishScan(null,R.string.review_gps_timeout);
    private final ActivityResultLauncher<String[]> permission=registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),result->{
        if(Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))||Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION)))scan();
        else show(R.string.review_gps_permission_denied);
    });
    @Override protected void onCreate(Bundle state){super.onCreate(state);if(com.gmail.manhle240406.project.wiring.UnavailableScreen.managed(this))return;EdgeToEdge.enable(this);setContentView(R.layout.activity_nearby_map);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.map_root),(v,insets)->{Insets bars=insets.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        findViewById(R.id.map_back).setOnClickListener(v->finish());status=findViewById(R.id.map_status);scan=findViewById(R.id.map_scan);locations=(LocationManager)getSystemService(Context.LOCATION_SERVICE);scan.setOnClickListener(v->scan());}
    private void scan(){boolean fine=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PermissionChecker.PERMISSION_GRANTED,coarse=ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PermissionChecker.PERMISSION_GRANTED;
        if(!fine&&!coarse){permission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION});return;}
        if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)&&!locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle(R.string.review_gps_disabled).setMessage(R.string.review_gps_disabled_help).setPositiveButton(R.string.review_open_settings,(d,w)->startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))).setNegativeButton(android.R.string.cancel,null).show();return;}
        show(R.string.review_gps_loading);scan.setEnabled(false);timeout.postDelayed(timedOut,12000);String provider=locations.isProviderEnabled(LocationManager.GPS_PROVIDER)?LocationManager.GPS_PROVIDER:LocationManager.NETWORK_PROVIDER;
        try{if(android.os.Build.VERSION.SDK_INT>=30){cancellation=new CancellationSignal();locations.getCurrentLocation(provider,cancellation,getMainExecutor(),location->finishScan(location,R.string.review_gps_empty));}else locations.requestSingleUpdate(provider,location->finishScan(location,R.string.review_gps_empty),getMainLooper());}catch(SecurityException denied){show(R.string.review_gps_permission_denied);}}
    private void finishScan(Location location,int fallback){timeout.removeCallbacks(timedOut);if(cancellation!=null){cancellation.cancel();cancellation=null;}scan.setEnabled(true);if(location==null){show(fallback);return;}status.setText(getString(R.string.review_gps_result,location.getLatitude(),location.getLongitude()));status.announceForAccessibility(status.getText());}
    private void show(int message){timeout.removeCallbacks(timedOut);if(scan!=null)scan.setEnabled(true);if(status!=null){status.setText(message);status.announceForAccessibility(getString(message));}}
    @Override protected void onDestroy(){timeout.removeCallbacks(timedOut);if(cancellation!=null)cancellation.cancel();super.onDestroy();}
}
