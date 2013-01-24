package com.rc.mockgpspath;

import java.util.ArrayList;
import java.util.Iterator;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

/**
 * Core service. Updates the fake location using standard Android APIs and
 * calculates the correct current position.
 * 
 * @author Ryan
 * 
 */
public class PlayLocationsService extends Service {
	private static final float GPS_ACCURACY = 10.0f;

	/**
	 * Special instance variable that is set whenever this service is running.
	 * Allows easy access to the service without having to use a binder. Only
	 * works from within the same process, however, which is fine for our use.
	 */
	static PlayLocationsService instance = null;

	UpdateGPSThread currentThread = null;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent.getStringExtra("action").equalsIgnoreCase(
				"com.rc.mockgpspath.start")) {
			handleStartCommand(intent);
		}

		if (intent.getStringExtra("action").equalsIgnoreCase(
				"com.rc.mockgpspath.stop")) {
			handleStopCommand();
		}

		return START_STICKY;
	}

	private void handleStartCommand(Intent intent) {
		if (currentThread != null) {
			currentThread.stopUpdating();
		}

		ArrayList<Location> startLocations = intent
				.getParcelableArrayListExtra("LOCATIONS");
		currentThread = new UpdateGPSThread(startLocations);
		currentThread.start();
	}

	private void handleStopCommand() {
		if (currentThread != null) {
			currentThread.stopUpdating();
			currentThread.interrupt();
			currentThread = null;
			stopSelf();
		}
	}

	public void createProgressNotification() {
		Notification notification = new Notification(
				android.R.drawable.ic_menu_mylocation,
				getString(R.string.mockgpspath_running_),
				System.currentTimeMillis());
		notification.flags = Notification.FLAG_NO_CLEAR
				| Notification.FLAG_ONGOING_EVENT;

		Intent notificationIntent = new Intent(this, ReplayGpxActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(this,
				getString(R.string.mockgpspath_running_),
				getString(R.string.mockgpspath_running_), contentIntent);

		startForeground(1337, notification);
	}

	public void removeProgressNotification() {
		stopForeground(true);
	}

	/**
	 * The actual worker thread that will update the GPS location.
	 */
	private class UpdateGPSThread extends Thread {
		private final ArrayList<Location> locations;
		private volatile boolean running;

		public UpdateGPSThread(ArrayList<Location> locations) {
			this.locations = locations;
		}

		@Override
		public void run() {
			Log.i("MockGPSService", "Starting UpdateGPSThread");
			createProgressNotification();
			running = true;

			LocationManager locationManager = (LocationManager) getSystemService("location");
			locationManager.addTestProvider("gps", false, false, false, false,
					false, true, true, 1, 1);
			locationManager.setTestProviderEnabled("gps", true);

			Iterator<Location> locationsIt = locations.iterator();
			Location loc = locationsIt.next();
			while (running && locationsIt.hasNext()) {
				long originalTime = loc.getTime();
				loc.setTime(System.currentTimeMillis());
				loc.setAccuracy(GPS_ACCURACY);
				locationManager.setTestProviderLocation(
						LocationManager.GPS_PROVIDER, loc);

				loc = locationsIt.next();
				long nextLocationTime = loc.getTime();
				try {
					Thread.sleep(nextLocationTime - originalTime);
				} catch (Exception e) {
				}
			}
			locationManager.setTestProviderEnabled("gps", false);
			locationManager.removeTestProvider("gps");
			removeProgressNotification();
			if (currentThread == this)
				currentThread = null;
			Log.i("MockGPSService", "Ending UpdateGPSThread");
		}

		public void stopUpdating() {
			running = false;
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		instance = this;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (instance == this)
			instance = null;
	}
}
