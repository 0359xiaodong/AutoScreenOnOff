package com.danielkao.autoscreenonoff;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

// TODO: add admob module at top of the screen
// TODO: disable turningOff while device is in landscape mode

/**
 * Created by plateau on 2013/05/20.
 */
public class ScreenOffWidgetConfigure extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{
    private static String CONFIGURE_ACTION="android.appwidget.action.APPWIDGET_CONFIGURE";

    private static final int REQUEST_CODE_DISABLE_ADMIN = 1;

    private DevicePolicyManager deviceManager;
    private ComponentName mDeviceAdmin;

    //service
    private SensorMonitorService sensorService;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (REQUEST_CODE_DISABLE_ADMIN == requestCode) {
            if (resultCode == Activity.RESULT_OK) {
                // Has become the device administrator.
            } else {
                //Canceled or failed: turn off Enabler
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePrefState();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.widget_configure);
        setContentView(R.layout.activity_settings);

        // for receiving pref change callbacks
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();

        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);

    }

    //<editor-fold desc="menu handlings">
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()){
            case R.id.menu_playstore_url:
            {
                String url = getString(R.string.playstore_url);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;
            }
            case R.id.menu_code_url:
            {
                String url = getString(R.string.author_url);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    //</editor-fold>

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode==KeyEvent.KEYCODE_BACK &&
                Integer.parseInt(Build.VERSION.SDK)<5) {
            onBackPressed();
        }

        return(super.onKeyDown(keyCode, event));
    }

    @Override
    public void onBackPressed() {
        if (CONFIGURE_ACTION.equals(getIntent().getAction())) {
            Intent intent=getIntent();
            Bundle extras=intent.getExtras();

            if (extras!=null) {
                int id=extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

                Intent result=new Intent();

                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                        id);
                setResult(RESULT_OK, result);
            }
        }

        super.onBackPressed();
    }

    private boolean isActiveAdmin() {
        return deviceManager.isAdminActive(mDeviceAdmin);
    }

    // uninstall button clicked
    public void uninstallApp(View view){
        deviceManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mDeviceAdmin = new ComponentName(this, TurnOffReceiver.class);

        // handle activeAdmin previlige
        if(isActiveAdmin()) {
            deviceManager.removeActiveAdmin(mDeviceAdmin);
        }
        Uri packageUri = Uri.parse("package:com.danielkao.autoscreenonoff");
        Intent uninstallIntent =
                new Intent(Intent.ACTION_DELETE, packageUri);
        startActivity(uninstallIntent);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,String key)
    {
        if(key.equals(ConstantValues.PREF_AUTO_ON)){
            // update state of pref disable in landscape
            updatePrefState();

            // send intent to service
            Intent i = new Intent(ConstantValues.SERVICE_INTENT_ACTION);
            i.putExtra(ConstantValues.SERVICEACTION,
                    ConstantValues.SERVICEACTION_TOGGLE);
            i.putExtra(ConstantValues.SERVICETYPE,
                    ConstantValues.SERVICETYPE_SETTING);
            startService(i);
        }
        else if(key.equals(ConstantValues.PREF_CHARGING_ON)){
            Intent i = new Intent(ConstantValues.SERVICE_INTENT_ACTION);
            i.putExtra(ConstantValues.SERVICEACTION,
                    (sharedPreferences.getBoolean(key,false))
                            ?ConstantValues.SERVICEACTION_TURNON
                            :ConstantValues.SERVICEACTION_TURNOFF);
                startService(i);
        }
    }

    // when auto on is turned on; user can't set charging mode
    // only when auto on is turned on, use can set landscape mode
    private void updatePrefState(){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        CheckBoxPreference cbpLandscape = (CheckBoxPreference) findPreference(ConstantValues.PREF_DISABLE_IN_LANDSCAPE);
        CheckBoxPreference cbpCharging = (CheckBoxPreference) findPreference(ConstantValues.PREF_CHARGING_ON);

        if(sp.getBoolean(ConstantValues.PREF_AUTO_ON, false)){
            cbpCharging.setEnabled(false);
            cbpLandscape.setEnabled(true);
        }else{
            cbpCharging.setEnabled(true);
            cbpLandscape.setEnabled(false);
        }

    }
}