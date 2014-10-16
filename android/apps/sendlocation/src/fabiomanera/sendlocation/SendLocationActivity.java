package fabiomanera.sendlocation;

import sneer.android.ui.MessageActivity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

public class SendLocationActivity extends MessageActivity implements LocationListener {

	protected LocationManager locationManager;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
	}
	
	@Override
	public void onLocationChanged(Location location) {
		if (location != null) {
			send("Location: ", "Latitude - " + location.getLatitude() + "Longitude - " + location.getLongitude());
			finish();
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {}

	@Override
	public void onProviderEnabled(String arg0) {}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {}

	@Override
	protected void onPause() {
		super.onPause();
		locationManager.removeUpdates(this);
	}
	
}
