package com.rc.mockgpspath;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;
import com.rc.mockgpspath.gpx.GpxParser;
import com.rc.mockgpspath.quickaction.ActionItem;
import com.rc.mockgpspath.quickaction.QuickAction;
import com.rc.mockgpspath.quickaction.QuickAction.OnActionItemClickListener;

/**
 * Handles GPX files operations. Contains a map view which is used by the user
 * to track the progress of the route.
 */
public class ReplayGpxActivity extends MapActivity {

	private final static int OK = 0;
	private final static int CANCEL = 1;
	private static int zoomLevel = 3;
	private static GeoPoint centerPoint = null;

	private MapView mapView;
	private ImageView trash, play, stop;
	private MyLocationOverlay myLocationOverlay;
	private ArrayList<Location> gpxPoints;

	private class ReadGpxTask extends
			AsyncTask<File, Void, ArrayList<Location>> {
		private ProgressDialog pd;

		@Override
		protected void onPreExecute() {
			pd = new ProgressDialog(ReplayGpxActivity.this);
			pd.setIndeterminate(true);
			pd.setTitle("Reading GPX...");
			pd.setMessage("Please wait while we read the GPX file");
			pd.setCancelable(false);
			pd.setCanceledOnTouchOutside(false);
			pd.show();
		}

		@Override
		protected ArrayList<Location> doInBackground(File... params) {
			BufferedInputStream in = null;
			try {
				File sourceFile = params[0];
				in = new BufferedInputStream(new FileInputStream(sourceFile));
				GpxParser parser = new GpxParser();
				return parser.parse(in);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (XmlPullParserException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
					}
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(ArrayList<Location> result) {
			pd.dismiss();
			setGpxPoints(result);
			readyMode();
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.replay);

		mapView = (MapView) findViewById(R.id.mapview);
		mapView.getController().setZoom(2);

		List<Overlay> overlays = mapView.getOverlays();
		myLocationOverlay = new MyLocationOverlay(ReplayGpxActivity.this,
				mapView);
		overlays.add(myLocationOverlay);

		trash = (ImageView) findViewById(R.id.trash);
		play = (ImageView) findViewById(R.id.play);
		stop = (ImageView) findViewById(R.id.stop);

		trash.setOnClickListener(trashClickListener);
		play.setOnClickListener(playClickListener);
		stop.setOnClickListener(stopClickListener);

		if (PlayLocationsService.instance != null
				&& PlayLocationsService.instance.currentThread != null) {
			// If the service is running, then we already have a path and are
			// following it.
			runningMode();
		} else {
			@SuppressWarnings("unchecked")
			ArrayList<Location> lastList = (ArrayList<Location>) getLastNonConfigurationInstance();

			if (lastList != null && lastList.size() > 0) {
				// If we already had a list in the last config, it means this is
				// just an activity change and we need to re-add all of the
				// previous points.
				gpxPoints = lastList;
				readyMode();
			} else {
				// No points, regular start mode
				startupMode();
			}
		}

		mapView.getController().setZoom(zoomLevel);
		if (centerPoint != null)
			mapView.getController().setCenter(centerPoint);

		Uri uri = getIntent().getData();
		if (uri != null) {
			File sourceFile = new File(uri.getPath());
			AsyncTask<File, Void, ArrayList<Location>> readGpxTask = new ReadGpxTask();
			readGpxTask.execute(sourceFile);
		}
	}

	public void setGpxPoints(ArrayList<Location> gpxPoints) {
		this.gpxPoints = gpxPoints;
	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		return gpxPoints;
	}

	public void startupMode() {
		hideView(play);
		hideView(stop);
		hideView(trash);
	}

	public void readyMode() {
		hideView(stop);
		showView(play);
		showView(trash);
	}

	public void runningMode() {
		showView(stop);
		hideView(play);
		hideView(trash);
	}

	@Override
	protected void onResume() {
		super.onResume();
		myLocationOverlay.enableMyLocation();
		checkIfMockEnabled();
	}

	private void checkIfMockEnabled() {
		try {
			int mock_location = Settings.Secure.getInt(getContentResolver(),
					"mock_location");
			if (mock_location == 0) {
				try {
					Settings.Secure.putInt(getContentResolver(),
							"mock_location", 1);
				} catch (Exception ex) {
				}
				mock_location = Settings.Secure.getInt(getContentResolver(),
						"mock_location");
			}

			if (mock_location == 0) {
				showDialog(EnableMockLocationDialogFragment.MOCKDIALOG);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		myLocationOverlay.disableMyLocation();
		zoomLevel = mapView.getZoomLevel();
		centerPoint = mapView.getMapCenter();
	}

	@Override
	protected boolean isRouteDisplayed() {
		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		// This is not done using a fragment directly as MapActivity does not
		// allow fragments...
		if (id == EnableMockLocationDialogFragment.MOCKDIALOG) {
			return EnableMockLocationDialogFragment.createDialog(this);
		}
		return super.onCreateDialog(id);
	};

	/**
	 * Convenience method to show a view with animation
	 * 
	 * @param v
	 *            The view.
	 */
	public void showView(View v) {
		if (v.getVisibility() == View.VISIBLE)
			return;

		v.setVisibility(View.VISIBLE);
		popinAnim(v);
	}

	/**
	 * Convenience method to hide a view with animation
	 * 
	 * @param v
	 *            The view.
	 */
	public void hideView(View v) {
		if (v.getVisibility() == View.GONE)
			return;

		v.setVisibility(View.GONE);
		popoutAnim(v);
	}

	/**
	 * A basic fade in + resize animation
	 * 
	 * @param v
	 */
	public void popinAnim(View v) {
		v.clearAnimation();

		AnimationSet as = new AnimationSet(false);

		AlphaAnimation a = new AlphaAnimation(0f, 1f);
		a.setDuration(300);
		as.addAnimation(a);

		ScaleAnimation s = new ScaleAnimation(0f, 1f, 0f, 1f,
				Animation.RELATIVE_TO_SELF, 0f, Animation.RELATIVE_TO_SELF,
				0.5f);
		s.setDuration(300);
		as.addAnimation(s);

		v.startAnimation(as);
	}

	/**
	 * A basic fade out + resize animation
	 * 
	 * @param v
	 */
	public void popoutAnim(View v) {
		v.clearAnimation();

		AnimationSet as = new AnimationSet(false);

		AlphaAnimation a = new AlphaAnimation(1f, 0f);
		a.setDuration(300);
		as.addAnimation(a);

		ScaleAnimation s = new ScaleAnimation(1f, 0f, 1f, 0f,
				Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
				0.5f);
		s.setDuration(300);
		as.addAnimation(s);

		v.startAnimation(as);
	}

	private OnClickListener trashClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			QuickAction quickAction = new QuickAction(ReplayGpxActivity.this);
			TextView textView = new TextView(ReplayGpxActivity.this);
			textView.setText("Clear current paths");
			quickAction.addDividerView(textView);

			ActionItem item = new ActionItem();
			item.setTitle(getString(R.string.ok));
			item.setActionId(OK);
			item.setIcon(getResources().getDrawable(R.drawable.trash));
			quickAction.addActionItem(item);

			item = new ActionItem();
			item.setTitle(getString(R.string.cancel));
			item.setActionId(CANCEL);
			item.setIcon(getResources().getDrawable(R.drawable.cancel));
			quickAction.addActionItem(item);

			quickAction
					.setOnActionItemClickListener(new OnActionItemClickListener() {

						@Override
						public void onItemClick(QuickAction source, int pos,
								int actionId) {
							source.dismiss();

							if (actionId == OK) {
								gpxPoints = null;
								startupMode();
							}
						}
					});

			quickAction.show(v);
		}
	};

	private OnClickListener playClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			LayoutInflater inflater = LayoutInflater
					.from(ReplayGpxActivity.this);
			View start = inflater.inflate(R.layout.start, null);

			final TextView distanceTV = (TextView) start
					.findViewById(R.id.distance);
			final TextView speedTV = (TextView) start.findViewById(R.id.speed);
			final TextView elapsetimeTV = (TextView) start
					.findViewById(R.id.elapsetime);
			final TextView finishtimeTV = (TextView) start
					.findViewById(R.id.finishtime);
			final SeekBar speedSeek = (SeekBar) start
					.findViewById(R.id.speedbar);
			final CheckBox randomizespeed = (CheckBox) start
					.findViewById(R.id.randomizespeed);

			distanceTV.setText("-");
			speedTV.setText("-");
			elapsetimeTV.setText("-");
			finishtimeTV.setText("-");
			speedSeek.setEnabled(false);
			randomizespeed.setEnabled(false);
			QuickAction quickAction = new QuickAction(ReplayGpxActivity.this);
			quickAction.addDividerView(start);


			ActionItem item = new ActionItem();
			item.setTitle("Start Mock GPS");
			item.setIcon(getResources().getDrawable(R.drawable.play));
			quickAction.addActionItem(item);

			quickAction
					.setOnActionItemClickListener(new OnActionItemClickListener() {

						@Override
						public void onItemClick(QuickAction source, int pos,
								int actionId) {
							source.dismiss();

							int progress = speedSeek.getProgress();
							if (progress > 100) {
								progress -= 90;
								progress *= progress;
							}
							double MperSec = progress * 1000.0 / 3600.0;

							startMockPaths(MperSec, randomizespeed.isChecked());
						}
					});

			quickAction.show(v);
		}
	};

	/**
	 * Sends an intent to start the MockGPSPathService with the node items
	 * stored inside the current map.
	 * 
	 * @param MperSec
	 *            The speed that the path should be followed.
	 * @param randomizespeed
	 *            Whether or not to use a random speed.
	 */
	void startMockPaths(double MperSec, boolean randomizespeed) {
		Intent i = new Intent(ReplayGpxActivity.this,
				PlayLocationsService.class);

		i.putExtra("action", "com.rc.mockgpspath.start");
		i.putExtra("MperSec", MperSec);
		i.putExtra("randomizespeed", randomizespeed);

		i.putParcelableArrayListExtra("LOCATIONS", gpxPoints);

		startService(i);

		runningMode();
		myLocationOverlay.disableMyLocation();

		new Thread() {
			public void run() {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
				}
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						myLocationOverlay.enableMyLocation();
					}
				});
			};
		}.start();
	}

	private OnClickListener stopClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			QuickAction quickAction = new QuickAction(ReplayGpxActivity.this);
			TextView textView = new TextView(ReplayGpxActivity.this);
			textView.setText(R.string.stop_current_mock_paths);
			quickAction.addDividerView(textView);

			ActionItem item = new ActionItem();
			item.setTitle(getString(R.string.ok));
			item.setActionId(OK);
			item.setIcon(getResources().getDrawable(R.drawable.cancel));
			quickAction.addActionItem(item);

			item = new ActionItem();
			item.setTitle(getString(R.string.cancel));
			item.setActionId(CANCEL);
			quickAction.addActionItem(item);

			quickAction
					.setOnActionItemClickListener(new OnActionItemClickListener() {

						@Override
						public void onItemClick(QuickAction source, int pos,
								int actionId) {
							source.dismiss();

							if (actionId == OK) {
								Intent i = new Intent(ReplayGpxActivity.this,
										PlayLocationsService.class);

								i.putExtra("action", "com.rc.mockgpspath.stop");

								startService(i);
								readyMode();
							}
						}
					});

			quickAction.show(v);
		}
	};
}