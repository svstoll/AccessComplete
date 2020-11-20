/*
 * AccessComplete, an easy to use editor of accessibility related
 * OpenStreetMap data for Android.  This program is a fork of
 * StreetComplete (https://github.com/westnordost/StreetComplete).
 *
 * Copyright (C) 2016-2020 Tobias Zwick and contributors (StreetComplete authors)
 * Copyright (C) 2020 Sven Stoll (AccessComplete author)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ch.uzh.ifi.accesscomplete;

import android.content.res.Configuration;
import android.graphics.Point;

import androidx.annotation.NonNull;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.AnyThread;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import javax.inject.Inject;

import de.westnordost.osmapi.common.errors.OsmApiException;
import de.westnordost.osmapi.common.errors.OsmApiReadResponseException;
import de.westnordost.osmapi.common.errors.OsmAuthorizationException;
import de.westnordost.osmapi.common.errors.OsmConnectionException;
import de.westnordost.osmapi.map.data.LatLon;
import de.westnordost.osmapi.map.data.OsmLatLon;
import ch.uzh.ifi.accesscomplete.controls.NotificationButtonFragment;
import ch.uzh.ifi.accesscomplete.data.download.DownloadItem;
import ch.uzh.ifi.accesscomplete.data.notifications.Notification;
import ch.uzh.ifi.accesscomplete.data.notifications.NotificationsSource;
import ch.uzh.ifi.accesscomplete.data.quest.Quest;
import ch.uzh.ifi.accesscomplete.data.quest.QuestAutoSyncer;
import ch.uzh.ifi.accesscomplete.data.quest.QuestController;
import ch.uzh.ifi.accesscomplete.data.download.DownloadProgressListener;
import ch.uzh.ifi.accesscomplete.data.download.QuestDownloadController;
import ch.uzh.ifi.accesscomplete.data.quest.UnsyncedChangesCountSource;
import ch.uzh.ifi.accesscomplete.data.upload.UploadController;
import ch.uzh.ifi.accesscomplete.data.upload.UploadProgressListener;
import ch.uzh.ifi.accesscomplete.data.upload.VersionBannedException;
import ch.uzh.ifi.accesscomplete.data.user.UserController;
import ch.uzh.ifi.accesscomplete.location.LocationRequestFragment;
import ch.uzh.ifi.accesscomplete.location.LocationState;
import ch.uzh.ifi.accesscomplete.location.LocationUtil;
import ch.uzh.ifi.accesscomplete.map.MainFragment;
import ch.uzh.ifi.accesscomplete.notifications.NotificationsContainerFragment;
import ch.uzh.ifi.accesscomplete.map.tangram.CameraPosition;
import ch.uzh.ifi.accesscomplete.util.CrashReportExceptionHandler;
import ch.uzh.ifi.accesscomplete.tutorial.TutorialFragment;
import ch.uzh.ifi.accesscomplete.util.GeoLocation;
import ch.uzh.ifi.accesscomplete.util.GeoUriKt;
import ch.uzh.ifi.accesscomplete.view.dialogs.RequestLoginDialog;

public class MainActivity extends AppCompatActivity implements
		MainFragment.Listener,
		TutorialFragment.Listener,
		NotificationButtonFragment.Listener {
	@Inject
	CrashReportExceptionHandler crashReportExceptionHandler;

	@Inject
	LocationRequestFragment locationRequestFragment;
	@Inject
	QuestAutoSyncer questAutoSyncer;

	@Inject
	QuestController questController;
	@Inject
	QuestDownloadController questDownloadController;
	@Inject
	UploadController uploadController;
	@Inject
	NotificationsSource notificationsSource;
	@Inject
	UnsyncedChangesCountSource unsyncedChangesCountSource;

	@Inject
	SharedPreferences prefs;
	@Inject
	UserController userController;

	// per application start settings
	private static boolean hasAskedForLocation = false;
	private static boolean dontShowRequestAuthorizationAgain = false;

	private MainFragment mainFragment;

	private final BroadcastReceiver locationAvailabilityReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			updateLocationAvailability();
		}
	};

	private final BroadcastReceiver locationRequestFinishedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			LocationState state = LocationState.valueOf(intent.getStringExtra(LocationRequestFragment.STATE));
			onLocationRequestFinished(state);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Injector.INSTANCE.getApplicationComponent().inject(this);

		getLifecycle().addObserver(questAutoSyncer);

		crashReportExceptionHandler.askUserToSendCrashReportIfExists(this);

		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

		if (prefs.getBoolean(Prefs.KEEP_SCREEN_ON, false)) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}

		getSupportFragmentManager().beginTransaction()
				.add(locationRequestFragment, LocationRequestFragment.class.getSimpleName())
				.commit();

		setContentView(R.layout.activity_main);

		mainFragment = (MainFragment) getSupportFragmentManager().findFragmentById(R.id.map_fragment);

		if (savedInstanceState == null) {
			boolean hasShownTutorial = prefs.getBoolean(Prefs.HAS_SHOWN_TUTORIAL, false);
			hasShownTutorial = false;
			if (!hasShownTutorial && !userController.isLoggedIn()) {
				getSupportFragmentManager().beginTransaction()
						.setCustomAnimations(R.anim.fade_in_from_bottom, R.anim.fade_out_to_bottom)
						.add(R.id.fragment_container, new TutorialFragment())
						.commit();
			}
			if (userController.isLoggedIn() && isConnected()) {
				userController.updateUser();
			}
		}

		handleGeoUri();
	}

	private void handleGeoUri() {
		Intent intent = getIntent();
		if (!Intent.ACTION_VIEW.equals(intent.getAction())) return;
		Uri data = intent.getData();
		if (data == null) return;
		if (!"geo".equals(data.getScheme())) return;

		GeoLocation geoLocation = GeoUriKt.parseGeoUri(data);
		if (geoLocation == null) return;

		float zoom;
		if (geoLocation.getZoom() == null || geoLocation.getZoom() < 14) {
			zoom = 18;
		} else {
			zoom = geoLocation.getZoom();
		}
		LatLon pos = new OsmLatLon(geoLocation.getLatitude(), geoLocation.getLongitude());
		mainFragment.setCameraPosition(pos, zoom);
	}

	@Override
	public void onStart() {
		super.onStart();

		registerReceiver(locationAvailabilityReceiver, LocationUtil.createLocationAvailabilityIntentFilter());

		LocalBroadcastManager localBroadcaster = LocalBroadcastManager.getInstance(this);

		localBroadcaster.registerReceiver(locationRequestFinishedReceiver,
				new IntentFilter(LocationRequestFragment.ACTION_FINISHED));

		questDownloadController.setShowNotification(false);

		uploadController.addUploadProgressListener(uploadProgressListener);
		questDownloadController.addDownloadProgressListener(downloadProgressListener);

		if (!hasAskedForLocation && !prefs.getBoolean(Prefs.LAST_LOCATION_REQUEST_DENIED, false)) {
			locationRequestFragment.startRequest();
		} else {
			updateLocationAvailability();
		}
	}

	@Override
	public void onBackPressed() {
		if (mainFragment != null) {
			if (!mainFragment.onBackPressed()) super.onBackPressed();
		}
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		final int keyCode = event.getKeyCode();
		if (keyCode == KeyEvent.KEYCODE_MENU && mainFragment != null) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				mainFragment.onClickMainMenu();
			}
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mainFragment != null) {
			CameraPosition camera = mainFragment.getCameraPosition();
			if (camera != null) {
				LatLon pos = camera.getPosition();
				prefs.edit()
						.putLong(Prefs.MAP_LATITUDE, Double.doubleToRawLongBits(pos.getLatitude()))
						.putLong(Prefs.MAP_LONGITUDE, Double.doubleToRawLongBits(pos.getLongitude()))
						.apply();
			}
		}
	}

	@Override
	public void onStop() {
		super.onStop();

		LocalBroadcastManager localBroadcaster = LocalBroadcastManager.getInstance(this);
		localBroadcaster.unregisterReceiver(locationRequestFinishedReceiver);

		unregisterReceiver(locationAvailabilityReceiver);

		questDownloadController.setShowNotification(true);

		uploadController.removeUploadProgressListener(uploadProgressListener);
		questDownloadController.removeDownloadProgressListener(downloadProgressListener);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		findViewById(R.id.main).requestLayout();
	}

	private void ensureLoggedIn() {
		if (questAutoSyncer.isAllowedByPreference()) {
			if (!userController.isLoggedIn()) {
				// new users should not be immediately pestered to login after each change (#1446)
				if (unsyncedChangesCountSource.getCount() >= 3 && !dontShowRequestAuthorizationAgain) {
					// TODO sst: dont' show during usability testing.
					new RequestLoginDialog(this).show();
					dontShowRequestAuthorizationAgain = true;
				}
			}
		}
	}

	private boolean isConnected() {
		ConnectivityManager connectivityManager = ContextCompat.getSystemService(this, ConnectivityManager.class);
		if (connectivityManager == null) return false;
		NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	/* ------------------------------ Upload progress listener ---------------------------------- */

	private final UploadProgressListener uploadProgressListener
			= new UploadProgressListener() {
		@AnyThread
		@Override
		public void onStarted() {
		}

		@Override
		public void onProgress(boolean success) {
		}

		@AnyThread
		@Override
		public void onError(@NonNull final Exception e) {
			runOnUiThread(() ->
			{
				if (e instanceof VersionBannedException) {
					String message = getString(R.string.version_banned_message);
					VersionBannedException vbe = (VersionBannedException) e;
					if (vbe.getBanReason() != null) {
						message += "\n\n" + vbe.getBanReason();
					}

					AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
							.setMessage(message)
							.setPositiveButton(android.R.string.ok, null)
							.create();

					dialog.show();

					// Makes links in the alert dialog clickable
					View messageView = dialog.findViewById(android.R.id.message);
					if (messageView instanceof TextView) {
						TextView messageText = (TextView) messageView;
						messageText.setMovementMethod(LinkMovementMethod.getInstance());
						Linkify.addLinks(messageText, Linkify.WEB_URLS);
					}
				} else if (e instanceof OsmConnectionException) {
					// a 5xx error is not the fault of this app. Nothing we can do about it, so
					// just notify the user
					Toast.makeText(MainActivity.this, R.string.upload_server_error, Toast.LENGTH_LONG).show();
				} else if (e instanceof OsmAuthorizationException) {
					// delete secret in case it failed while already having a token -> token is invalid
					userController.logOut();
					new RequestLoginDialog(MainActivity.this).show();
				} else {
					crashReportExceptionHandler.askUserToSendErrorReport(
							MainActivity.this, R.string.upload_error, e);
				}
			});
		}

		@AnyThread
		@Override
		public void onFinished() {
		}
	};

	/* ----------------------------- Download Progress listener  -------------------------------- */

	private final DownloadProgressListener downloadProgressListener
			= new DownloadProgressListener() {
		@AnyThread
		@Override
		public void onStarted() {
		}

		@Override
		public void onFinished(@NonNull DownloadItem item) {
		}

		@Override
		public void onStarted(@NonNull DownloadItem item) {
		}

		@AnyThread
		@Override
		public void onError(@NonNull final Exception e) {
			runOnUiThread(() ->
			{
				// a 5xx error is not the fault of this app. Nothing we can do about it, so it does
				// not make sense to send an error report. Just notify the user. Further, we treat
				// the following errors the same as a (temporary) connection error:
				// - an invalid response (OsmApiReadResponseException)
				// - request timeout (OsmApiException with error code 408)
				boolean isEnvironmentError =
						e instanceof OsmConnectionException ||
								e instanceof OsmApiReadResponseException ||
								(e instanceof OsmApiException && ((OsmApiException) e).getErrorCode() == 408);
				if (isEnvironmentError) {
					Toast.makeText(MainActivity.this, R.string.download_server_error, Toast.LENGTH_LONG).show();
				} else {
					crashReportExceptionHandler.askUserToSendErrorReport(MainActivity.this, R.string.download_error, e);
				}
			});
		}

		@AnyThread
		@Override
		public void onSuccess() {
		}

		@AnyThread
		@Override
		public void onFinished() {
		}
	};

	/* --------------------------------- NotificationButtonFragment.Listener ---------------------------------- */

	@Override
	public void onClickShowNotification(@NonNull Notification notification) {
		Fragment f = getSupportFragmentManager().findFragmentById(R.id.notifications_container_fragment);
		((NotificationsContainerFragment) f).showNotification(notification);
	}

	/* --------------------------------- MainFragment.Listener ---------------------------------- */

	@Override
	public void onQuestSolved(@Nullable Quest quest, @Nullable String source) {
		ensureLoggedIn();
	}

	@Override
	public void onCreatedNote(@NonNull Point screenPosition) {
		ensureLoggedIn();
	}

	/* ------------------------------- TutorialFragment.Listener -------------------------------- */


	@Override
	public void onFinishedTutorial() {
		prefs.edit().putBoolean(Prefs.HAS_SHOWN_TUTORIAL, true).apply();
		Fragment tutorialFragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
		if (tutorialFragment != null) {
			getSupportFragmentManager().beginTransaction()
					.setCustomAnimations(R.anim.fade_in_from_bottom, R.anim.fade_out_to_bottom)
					.remove(tutorialFragment)
					.commit();
		}
	}

	/* ------------------------------------ Location listener ----------------------------------- */

	private void updateLocationAvailability() {
		if (LocationUtil.isLocationOn(this)) {
			questAutoSyncer.startPositionTracking();
		} else {
			questAutoSyncer.stopPositionTracking();
		}
	}

	private void onLocationRequestFinished(LocationState withLocationState) {
		hasAskedForLocation = true;
		boolean enabled = withLocationState.isEnabled();
		prefs.edit().putBoolean(Prefs.LAST_LOCATION_REQUEST_DENIED, !enabled).apply();

		if (enabled) {
			updateLocationAvailability();
		} else {
			Toast.makeText(MainActivity.this, R.string.no_gps_no_quests, Toast.LENGTH_LONG).show();
		}
	}
}