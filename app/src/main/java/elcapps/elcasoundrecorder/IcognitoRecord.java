package elcapps.elcasoundrecorder;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

public class IcognitoRecord extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences preferences = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        boolean recording = isMyServiceRunning(RService.class);
        if(recording) {
            stopRecording();
        } else {
            startRecording();
        }
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
        finish();
    }

    private void startRecording() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.apply();
        editor.putBoolean("recording", true);
        startService(new Intent(this, RService.class));
    }

    private void stopRecording() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("recording", false);
        editor.apply();
        sendBroadcast(new Intent("elcarecorder.stop"));
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
