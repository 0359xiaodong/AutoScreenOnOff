package com.danielkao.autoscreenonoff;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.widget.RemoteViews;
import android.widget.Toast;

public class SensorMonitorService extends Service implements
		SensorEventListener {
	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();

	private SensorManager mSensorManager;
	private PowerManager mPowerManager;
	private Sensor mProximity;

	private boolean mIsRegistered;

	private WakeLock partialLock, screenLock;

	DevicePolicyManager deviceManager;
	ComponentName mDeviceAdmin;

	private boolean isActiveAdmin() {
		return deviceManager.isAdminActive(mDeviceAdmin);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// being restarted
		if (intent == null) {
			ConstantValues.logv("onStartCommand: no intent");
			if (ConstantValues.getPrefAutoOnoff(this) == false) {
				unregisterSensor();
			} else {
				registerSensor();
			}

			return START_STICKY;
		}

		int action = intent.getIntExtra(ConstantValues.SERVICEACTION, -1);

        // from widget
		if (action == ConstantValues.SERVICEACTION_TOGGLE) {
			// do the toggle first
			togglePreference();
			updateWidgetUI(intent);

			if (ConstantValues.getPrefAutoOnoff(this) == false) {
				unregisterSensor();
			} else {
				registerSensor();
			}
		} else if(action == ConstantValues.SERVICEACTION_TURNON){
            // from charging receiver
            if(!isRegistered()){
                registerSensor();
                updateWidgetCharging(true);
            }

        } else if(action == ConstantValues.SERVICEACTION_TURNOFF){
            // from charging receiver
            if(isRegistered() && !ConstantValues.getPrefAutoOnoff(this)){
                unregisterSensor();
                updateWidgetCharging(false);
            }

        }

		return START_STICKY;
	}

    private void updateWidgetCharging(boolean b) {
        Intent i = new Intent(this, ToggleAutoScreenOnOffAppWidgetProvider.class);
        i.setAction(ConstantValues.UPDATE_WIDGET_ACTION);
        i.putExtra(ConstantValues.PREF_CHARGING_ON, b);
        this.sendBroadcast(i);
    }

    //
	// life cycle
	//
	public SensorMonitorService() {
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		deviceManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		mDeviceAdmin = new ComponentName(this, TurnOffReceiver.class);

		mPowerManager = ((PowerManager) getSystemService(POWER_SERVICE));
		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mProximity = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);

		partialLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				"TAG");
		screenLock = mPowerManager.newWakeLock(
				PowerManager.ACQUIRE_CAUSES_WAKEUP
						| PowerManager.FULL_WAKE_LOCK
						| PowerManager.ON_AFTER_RELEASE, "TAG");
	}

	@Override
	public void onDestroy() {
		ConstantValues.logv("onDestroy");
		unregisterSensor();
		super.onDestroy();
	}

	// to return service class
	public class LocalBinder extends Binder {
		SensorMonitorService getService() {
			return SensorMonitorService.this;
		}
	}

	//
	// pubilc API for client
	//
	public void registerSensor() {
		ConstantValues.logv("registerSensor");
		if (mIsRegistered) {
			Toast.makeText(SensorMonitorService.this,
					"Auto Screen On/off is already turned on",
					Toast.LENGTH_SHORT).show();
			return;
		}
		
		// grant device management
		if(!isActiveAdmin()){
			Intent i = new Intent(this, MainActivity.class);
			i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			this.startActivity(i);
		}

		mSensorManager.registerListener(this, mProximity,
				SensorManager.SENSOR_DELAY_NORMAL);
		mIsRegistered = true;

		if (partialLock != null)
			partialLock.acquire();

		String s = getResources().getString(R.string.turn_autoscreen_on);
		Toast.makeText(SensorMonitorService.this, s, Toast.LENGTH_SHORT).show();
	}

	public void unregisterSensor() {
		ConstantValues.logv("unregisterSensor");
		if (mIsRegistered) {
			mSensorManager.unregisterListener(this);
			String s = getResources().getString(R.string.turn_autoscreen_off);
			Toast.makeText(SensorMonitorService.this, s, Toast.LENGTH_SHORT).show();
		}

		if (partialLock != null && partialLock.isHeld())
			partialLock.release();
		mIsRegistered = false;

		stopSelf();
	}

	public boolean isRegistered() {
		return mIsRegistered;
	}

	//
	// listener
	//

	@Override
	public final void onAccuracyChanged(Sensor sensor, int accuracy) {
		ConstantValues.logv("onAccuracyChanged");
	}

	@SuppressLint("Wakelock")
	@Override
	public final void onSensorChanged(SensorEvent event) {
		// The light sensor returns a single value.
		// Many sensors return 3 values, one for each axis.
		float lux = event.values[0];

		// Do something with this sensor value.
		ConstantValues.logv("onSensorChanged:%f", lux);
		if (isActiveAdmin()) {
			// should turn off
			if (lux == 0f) {
				if (mPowerManager.isScreenOn()) {
					deviceManager.lockNow();
					ConstantValues.logv("turn off");
				}
			}
			// should turn on
			else {
				if (!mPowerManager.isScreenOn()) {
					ConstantValues.logv("turn on");
					if (!screenLock.isHeld()) {
						screenLock.acquire();

						// screenLock.release();
						new Thread(new Runnable() {
							public void run() {
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									e.printStackTrace();
								}
								screenLock.release();
							}
						}).start();
					}
				}
			}
		}
	}

	private void togglePreference() {
		SharedPreferences sp = getSharedPreferences(ConstantValues.PREF,
				Activity.MODE_PRIVATE);
		boolean IsAutoOn = sp.getBoolean(ConstantValues.IS_AUTO_ON, false);
		Editor editor = sp.edit();
		editor.putBoolean(ConstantValues.IS_AUTO_ON, !IsAutoOn);
		editor.commit();

	}

	private void updateWidgetUI(Intent intent) {
		int widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
				-1);
		if (widgetId == -1)
			return;

		// Create an Intent to interact with service 
		Intent i = new Intent(ConstantValues.SERVICE_INTENT_ACTION);
		i.putExtra(ConstantValues.SERVICEACTION,
				ConstantValues.SERVICEACTION_TOGGLE);
		i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

		PendingIntent pendingIntent = PendingIntent.getService(this, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT);

		AppWidgetManager appWidgetMan = AppWidgetManager.getInstance(this);
		RemoteViews views = new RemoteViews(this.getPackageName(),
				R.layout.toggleonoff_appwidget);
		views.setOnClickPendingIntent(R.id.imageview, pendingIntent);

		// update UI if necessary
		boolean autoOn = ConstantValues.getPrefAutoOnoff(this);
		if (autoOn) {
			// set icon to on
			views.setImageViewResource(R.id.imageview, R.drawable.widget_on);
		} else {
			// set icon to off
			views.setImageViewResource(R.id.imageview, R.drawable.widget_off);
		}

		appWidgetMan.updateAppWidget(widgetId, views);
	}

}
