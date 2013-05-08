package com.danielkao.poweroff;

import java.util.Arrays;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.Log;

public final class ConstantValues {
	public static final String TAG = "SensorMonitor";
	public static final String PREF = "SensorMonitorPref";
	public static final String IS_AUTO_ON = "is_audo_on";
	public static final String TOGGLE_AUTO = "toggle_auto";
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	public static void logv(Object...argv){
		if(argv.length == 1)
			Log.v(TAG, (String) argv[0]);
		else
		{
			Object [] slicedObj = Arrays.copyOfRange(argv, 1, argv.length);
			Log.v(TAG,String.format((String) argv[0], (Object[])slicedObj));
		}
	}
	
	

}
