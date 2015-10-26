package elcapps.elcasoundrecorder;

import android.Manifest;
import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.rey.material.widget.Slider;

import net.rdrei.android.dirchooser.DirectoryChooserFragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class main extends AppCompatActivity implements
        DirectoryChooserFragment.OnFragmentInteractionListener {

    private boolean fingerDown = false;
    private boolean holdRecording = false;
    private boolean sliderVisible = false;
    private boolean holdToRecord = false;
    private boolean recording = false;
    private boolean paused;

    private int color_primary;
    private int color_primary_dark;
    private int seconds = 0;
    private int minutes = 0;
    private int sessionid=-1;
    private int recordingMode = 2;
    private int recordingid = 0;
    private int frequency = 44100;
    private int deletePosition = -1;

    private ImageButton pausebtn;
    private ImageButton stopbtn;
    private ImageButton colourbtn;
    private ImageButton folderbtn;

    private TextView timer;
    private TextView statusTV;

    private FloatingActionButton FAButton;

    private Toolbar toolbar;

    private FixedRecyclerView recyclerView;
    private RecyclerAdapter recyclerAdapter;
    private RecyclerAdapter.ViewHolder gHolder = null;

    private static String mFileName = null;
    private String folderPath;

    private LinearLayout bottombar;
    private LinearLayout bglayout;
    private RelativeLayout parent_view;

    private PlayClickListener playClickListener;
    private Spinner.OnItemSelectedListener spinner_item_listener;
    private DialogInterface.OnClickListener dialog_click_listener;
    private List<RecordedFile> recordings = new ArrayList<RecordedFile>();
    private Receiver receiver=null;
    private MediaPlayer mPlayer = null;
    private Thread t;
    private SharedPreferences pref;
    private DirectoryChooserFragment mDialogFragment;
    private AlertDialog qualitySettingsDialog;
    private File deleteFile = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActive(true); // sets activity state to active so service checks to send refresh broadcast
        setContentView(R.layout.home_xml);
        correctPrefs(); // helps migrate from old pref file to new pref
        initializeLayouts(); // initialize variables with IDs
        setLayoutColors(); // Dynamic theme engine
        setSupportActionBar(toolbar);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bglayout.setElevation(12);
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                checkApplicationPermissions();
            } else {
                if(recordingid == 0) {
                    showFeedbackDialog();
                }
            }
        } else {
            View view = findViewById(R.id.shadow_view);
            view.setVisibility(View.VISIBLE);
            if(recordingid == 0) {
                showFeedbackDialog();
            }
        }

        File fDir = new File(mFileName);
        if(!fDir.exists()) {
            try {
                fDir.mkdirs();
                File nomedia = new File(fDir.getAbsolutePath()+"/.nomedia");
                nomedia.createNewFile();
            } catch (Exception e){
                log(e.toString());
            }
        }
        setButtons();
        initializeDialogs();
        // --- RECYCLER VIEW INITIALIZE ---
        float mDensity = getResources().getDisplayMetrics().density;
        int space = (int) (4 * mDensity + 0.5f);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        recyclerView.setLayoutManager(linearLayoutManager);
        playClickListener = new PlayClickListener();
        recyclerAdapter = new RecyclerAdapter();
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new SpacesItemDecoration(space));
        recyclerView.setAdapter(recyclerAdapter);
        registerForContextMenu(recyclerView);

        // --- INITIALIZE FILE ARRAY ---
        updateFiles(1);


    }




    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemid = item.getItemId();
        if(itemid == R.id.menuitem_holdToRecord) {
            if(holdToRecord) {
                showSnack("Tap and hold to record disabled", true);
                holdToRecord = false;
                setHoldToRecord(false);
                setButtons();
            } else {
                showSnack("Tap and hold to record enabled", true);
                holdToRecord = true;
                setHoldToRecord(true);
                setButtons();
            }
        } else if( itemid == R.id.menuitem_qualitySettings) {
            qualitySettingsDialog.show();
        } else if (itemid == R.id.menuitem_feedback) {
            Intent intent = new Intent(this, FeedbackActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStop() {
        super.onStop();
        setActive(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        setActive(false);
        if(receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    public boolean onContextItemSelected(MenuItem item) {
        final int position = recyclerAdapter.getContextPosition();
        Log.e("ELCA", "position: " + position);
        File file = new File(recordings.get(position).path);
        final File f = file;
        switch (item.getItemId()) {
            case R.id.rename_menu:
                final Dialog dialog = new Dialog(main.this);
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                dialog.setContentView(R.layout.rename_dialog);

                final EditText editText = (EditText) dialog.findViewById(R.id.editText);


                Button renamebtn = (Button) dialog.findViewById(R.id.button);
                renamebtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(editText.getText().length() != 0) {
                            mFileName = getFolderPath() + editText.getText() + ".wav";
                            File renamed = new File(mFileName);
                            f.renameTo(renamed);
                            RecordedFile recordedFile = recordings.get(position);
                            recordedFile.filename = editText.getText() + ".wav";
                            recordedFile.path = mFileName;
                            recordings.set(position, recordedFile);
                            recyclerAdapter.notifyDataSetChanged();
                            dialog.dismiss();
                        }
                    }
                });
                Button cancel = (Button) dialog.findViewById(R.id.button2);
                cancel.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        dialog.dismiss();
                    }
                });

                dialog.show();
                return true;
            case R.id.delete_menu: {
                if (sessionid == position) {
                    stopPlaying();
                    sessionid = -1;
                }
                prepareDelete(position, file);
                AlertDialog deleteAlert = new AlertDialog.Builder(main.this, R.style.Theme_AppCompat_Light_Dialog_Alert)
                        .setPositiveButton(R.string.text_delete_caps, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                deleteRecording();
                            }
                        })
                        .setNegativeButton(R.string.text_cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                resetDelete();
                            }
                        })
                        .create();
                deleteAlert.setMessage(getString(R.string.text_delete_confirm));
                deleteAlert.show();
                return true;
            }
            case R.id.share_menu:
                Intent shareIntent = new Intent();
                shareIntent.setAction(Intent.ACTION_SEND);
                shareIntent.setType("audio/*");
                shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///" + f.getPath()));
                startActivity(Intent.createChooser(shareIntent, "Share " + f.getName()));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    public void prepareDelete(int position, File file) {
        deletePosition = position;
        deleteFile = file;
    }

    public void resetDelete() {
        deleteFile = null;
        deletePosition = -1;
    }

    public void deleteRecording() {
        if (deletePosition == -1) return;
        recordings.remove(deletePosition);
        recyclerAdapter.notifyItemRemoved(deletePosition);
        deleteFile.delete();
        Snackbar.make(parent_view, getString(R.string.notify_recording_deleted), Snackbar.LENGTH_SHORT).show();
        resetDelete();
    }

    public void stopNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.cancel(1);
    }

    public void createNotification(Context context) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setContentTitle("Sound Recorder by ELC");
        builder.setSmallIcon(R.drawable.notification);
        builder.setContentText(getString(R.string.notification_content_text));
        Intent intent = new Intent(getApplicationContext(), main.class);
        intent.putExtra("fromNotification", true);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
        builder.setContentIntent(contentIntent);
        Notification notification = builder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, notification);
    }

    private void startRecording() {
        receiver = new Receiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("elcarecorder.refresh");
        registerReceiver(receiver, filter);
        int filenumber;
        pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.apply();
        editor.putBoolean("recording", true);
        filenumber = pref.getInt(getString(R.string.lastclipid), 0);
        filenumber++;

        mFileName = getFolderPath();
        if(recordingMode == 1) {
            mFileName += "/ELCArecorder/recording " + filenumber + ".m4a";
            pausebtn.setEnabled(false);
        } else {
            mFileName += "/ELCArecorder/recording " + filenumber + ".wav";
        }
        startService(new Intent(this, RService.class));
        t = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                seconds++;
                                if (seconds == 60) {
                                    minutes++;
                                    seconds = 0;
                                }
                                updateTextView();
                            }
                        });
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };

        t.start();
        statusTV.setText(R.string.text_recording_started);
    }

    private void updateTextView() {
        String time,ts,tm;
        if(seconds < 10) {
            ts = "0" + String.valueOf(seconds);
        } else {
            ts = ""+ String.valueOf(seconds);
        }
        if(minutes <10) {
            tm = "0" + String.valueOf(minutes);
        } else {
            tm = ""+ String.valueOf(minutes);
        }
        time = tm+":"+ts;
        timer.setText(time);
    }

    private void stopRecording() {
        pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean("recording", false);
        editor.apply();
        sendBroadcast(new Intent("elcarecorder.stop"));
        seconds = 0;
        minutes = 0;
        if(t != null) {
            t.interrupt();
        }
        timer.setText(R.string.timeTV);
        statusTV.setText(R.string.text_recording_stopped);
    }


    @Override
    public void onPause() {
        super.onPause();
        setActive(false);
        if (mPlayer != null) {
            stopPlaying();
            sessionid = -1;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onResume() {
        log("Resume");
        setActive(true);
        super.onResume();
        Intent intent = this.getIntent();
        if(intent != null) {
            if (intent.getBooleanExtra("fromWidget", false)) {
                if (recording) {
                    stopFromWidget();
                } else {
                    recordFromWidget();
                }
                intent.putExtra("fromWidget",false);
                setIntent(intent);
            } else if(intent.getBooleanExtra("fromNotification", false)){
                if(recording) {
                    stopButtonPress();
                }
            }
            if(!recording) {
                updateFiles(1);
            }
            if (isMyServiceRunning(RService.class)) {
                showRecordLayout();
            }
        }
        else {
            if (isMyServiceRunning(RService.class)) {
                showRecordLayout();
            }
            if(!recording) {
                updateFiles(1);
            }
        }
    }


    private Animation shrinkOut() {
        Animation anim = new ScaleAnimation(1f,0f,1f,0f, Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF,0.5f);
        anim.setFillAfter(true);
        anim.setFillEnabled(true);
        anim.setDuration(250);
        anim.setInterpolator(new FastOutLinearInInterpolator());
        return anim;
    }

    private Animation growIn() {
        ScaleAnimation anim = new ScaleAnimation(0f,1f,0f,1f, Animation.RELATIVE_TO_SELF,0.5f, Animation.RELATIVE_TO_SELF,0.5f);
        anim.setFillAfter(true);
        anim.setFillEnabled(true);
        anim.setDuration(500);
        anim.setInterpolator(new LinearOutSlowInInterpolator());
        return anim;
    }


    public static String getFileExt(String fileName) {
        return fileName.substring((fileName.lastIndexOf(".") + 1), fileName.length());
    }

    public void updateFiles(int rType) {
        new ListUpdate(rType).execute();
    }

    public void setActive(boolean active) {
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(getString(R.string.activitystatus), active);
        editor.apply();
    }

    public void setHoldToRecord(boolean enabled) {
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putBoolean(getString(R.string.pref_holdToRecord), enabled);
        editor.apply();
    }

    public boolean getHoldToRecord() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.pref_holdToRecord), false);
    }

    public void setFolder(String folderPath) {
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putString(getString(R.string.folderPath), folderPath);
        editor.apply();
    }

    public String getFolder() {
        String defaultPath = Environment.getExternalStorageDirectory() + "/ELCArecorder/";
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getString(getString(R.string.folderPath), defaultPath);
    }

    private void savetoprefs() {
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt("color_primary", color_primary);
        editor.putInt("color_primary_dark",color_primary_dark);
        editor.apply();
    }

    public boolean getActive() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.activitystatus), true);
    }

    public int getFrequencyFromPref() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getInt(getString(R.string.frequency), 44100);
    }

    public int getFrequency() {
        return frequency;
    }

    public int getRecordingMode() {
        return recordingMode;
    }

    public void pauserecording() {
        sendBroadcast(new Intent("elcarecorder.pause"));
        paused = true;
        t.interrupt();
        statusTV.setText(R.string.text_recording_paused);
    }

    public void continuerecording() {
        paused= false;
        sendBroadcast(new Intent("elcarecorder.continue"));
        t = new Thread() {

            @Override
            public void run() {
                try {
                    while (!isInterrupted()) {
                        Thread.sleep(1000);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                seconds++;
                                if (seconds == 60) {
                                    minutes++;
                                    seconds = 0;
                                }
                                updateTextView();
                            }
                        });
                    }
                } catch (InterruptedException ignored) {
                }
            }
        };

        t.start();
        statusTV.setText(R.string.text_recording_started);
    }

    private void setLayoutColors() {
        bottombar.setBackgroundColor(color_primary_dark);
        //parent_view.setBackgroundColor(color_primary);
        toolbar.setBackgroundColor(color_primary);
        bglayout.setBackgroundColor(color_primary);
        pausebtn.setBackgroundColor(color_primary_dark);
        stopbtn.setBackgroundColor(color_primary_dark);
        //recyclerView.setBackgroundColor(color_primary);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color_primary_dark);
            Bitmap bm = BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher);
            ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, color_primary);
            setTaskDescription(taskDesc);
        }
    }

    private void correctPrefs() {
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int recordingFNumber = prefs.getInt(getString(R.string.lastclipid), 0);
        pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        if(recordingFNumber > pref.getInt(getString(R.string.lastclipid),0)) {
            SharedPreferences.Editor editor = pref.edit();
            editor.putInt(getString(R.string.lastclipid), recordingFNumber);
            editor.putInt(getString(R.string.mode),recordingMode);
            editor.apply();
        } else {
            frequency = pref.getInt(getString(R.string.frequency), 44100);
            recordingMode = pref.getInt(getString(R.string.mode), 2);
            recordingid = pref.getInt(getString(R.string.lastclipid), 0);
            holdToRecord = pref.getBoolean(getString(R.string.pref_holdToRecord), false);
            color_primary = pref.getInt("color_primary",getResources().getColor(R.color.primary));
            color_primary_dark = pref.getInt("color_primary_dark",getResources().getColor(R.color.primary_dark));
            mFileName = getFolder();
            folderPath = mFileName;
            log("Number: " + pref.getInt(getString(R.string.lastclipid), 0));
        }
    }


    public void setColor(int color) {
        Log.e("color",Integer.toString(color));
        switch (color) {
            case 0:
                color_primary = getResources().getColor(R.color.red_500);
                color_primary_dark = getResources().getColor(R.color.red_700);
                break;
            case 1:
                color_primary = getResources().getColor(R.color.pink_500);
                color_primary_dark = getResources().getColor(R.color.pink_700);
                break;
            case 2:
                color_primary = getResources().getColor(R.color.orange_500) ;
                color_primary_dark = getResources().getColor(R.color.orange_700) ;
                break;
            case 3:
                color_primary = getResources().getColor(R.color.yellow_700);
                color_primary_dark = getResources().getColor(R.color.yellow_800) ;
                break;
            case 4:
                color_primary = getResources().getColor(R.color.blue_500);
                color_primary_dark = getResources().getColor(R.color.blue_700) ;
                break;
            case 5:
                color_primary = getResources().getColor(R.color.light_blue_500) ;
                color_primary_dark = getResources().getColor(R.color.light_blue_700);
                break;
            case 6:
                color_primary = getResources().getColor(R.color.teal_500) ;
                color_primary_dark = getResources().getColor(R.color.teal_700) ;
                break;
            case 7:
                color_primary = getResources().getColor(R.color.cyan_500) ;
                color_primary_dark = getResources().getColor(R.color.cyan_700) ;
                break;
            case 8:
                color_primary = getResources().getColor(R.color.green_500) ;
                color_primary_dark = getResources().getColor(R.color.green_700) ;
                break;
            case 9:
                color_primary = getResources().getColor(R.color.light_green_500);
                color_primary_dark = getResources().getColor(R.color.light_green_700) ;
                break;
            case 10:
                color_primary = getResources().getColor(R.color.lime_500) ;
                color_primary_dark = getResources().getColor(R.color.lime_700) ;
                break;
            case 11:
                color_primary = getResources().getColor(R.color.purple_500) ;
                color_primary_dark = getResources().getColor(R.color.purple_700);
                break;
            case 12:
                color_primary = getResources().getColor(R.color.dark_purple_500);
                color_primary_dark = getResources().getColor(R.color.dark_purple_700) ;
                break;
            case 13:
                color_primary = getResources().getColor(R.color.amber_500);
                color_primary_dark = getResources().getColor(R.color.amber_700) ;
                break;
            case 14:
                color_primary = getResources().getColor(R.color.grey_500);
                color_primary_dark = getResources().getColor(R.color.grey_700) ;
                break;
            case 15:
                color_primary = getResources().getColor(R.color.blue_grey_500);
                color_primary_dark = getResources().getColor(R.color.blue_grey_700);
                break;
            case 16:
                color_primary = getResources().getColor(R.color.amoled_black);
                color_primary_dark = color_primary;
                break;
        }
        savetoprefs();
        setLayoutColors();
    }

    private void setFrequency(int freq) {
        frequency = freq;
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(getString(R.string.frequency), freq);
        editor.apply();
    }

    private void setFormat(int format) {
        if(format == 1) {
            recordingMode = 1;
        } else {
            recordingMode = 2;
        }
        pref = getSharedPreferences("recordingPrefs",Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(getString(R.string.mode), recordingMode);
        editor.apply();
    }

    private void initializeDialogs() {
        qualitySettingsDialog = QualitySettings();
    }

    private void setButtons() {

        spinner_item_listener = new Spinner.OnItemSelectedListener() {
            int count = 0;
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                int frequency = getFrequency();
                int recordingM = getRecordingMode();
                if (adapterView.getId() == R.id.spinner_frequency) {
                    int sFrequency = Integer.parseInt(adapterView.getAdapter().getItem(i).toString());
                    if(sFrequency != frequency) {
                        setFrequency(Integer.parseInt(adapterView.getAdapter().getItem(i).toString()));
                        log("test" + i);
                    }
                }
                if (adapterView.getId() == R.id.spinner_format) {
                    if(i == 0 && recordingM == 1) {
                        setFormat(i);
                        log("test" + i);
                    } else if (i == 1 && recordingM == 2) {
                        setFormat(i);
                        log("test"+i);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                log("test spinner");
            }
        };

        dialog_click_listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //Spinner spinner = (Spinner) ((AlertDialog) dialogInterface).findViewById(R.id.spinner_frequency);
            }
        };

        final View.OnClickListener buttons_listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int id = view.getId();
                if(id == R.id.recbutton) {
                    if (!recording) {
                        startRecording();
                        recording = true;
                        pausebtn = (ImageButton) findViewById(R.id.pause_btn);
                        stopbtn = (ImageButton) findViewById(R.id.stop_btn);
                        FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
                        bottombar = (LinearLayout) (findViewById(R.id.bottombar));

                        Animation anim = shrinkOut();
                        anim.setFillEnabled(false);
                        anim.setFillAfter(false);
                        FAButton.startAnimation(anim);
                        FAButton.setVisibility(View.GONE);
                        FAButton.setEnabled(false);

                        bottombar.setVisibility(View.VISIBLE);
                        if(recordingMode == 2) {
                            pausebtn.setEnabled(true);
                        }
                        stopbtn.setEnabled(true);
                        ObjectAnimator anima = ObjectAnimator.ofFloat(bottombar,"translationY",parent_view.getY()+bottombar.getHeight(),parent_view.getY());
                        anima.setInterpolator(new FastOutLinearInInterpolator());
                        anima.setDuration(250);
                        anima.setStartDelay(250);
                        anima.start();
                        createNotification(getApplicationContext());
                        log("FAB pressed");
                    }
                } else if (id == R.id.palette) {
                    ColorPicker dialog = new ColorPicker();
                    dialog.show(getSupportFragmentManager(), "null");
                } else if (id == R.id.folder) {
                    mDialogFragment = DirectoryChooserFragment.newInstance("DialogFragment", null);
                    mDialogFragment.show(getFragmentManager(), null);

                } else if (id == R.id.pause_btn) {
                    if(!paused) {
                        pausebtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_mic_white_24dp));
                        pauserecording();
                    } else {
                        pausebtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
                        continuerecording();
                    }
                } else if (id == R.id.stop_btn) {
                    recording = false;
                    pausebtn = (ImageButton) findViewById(R.id.pause_btn);
                    stopbtn = (ImageButton) findViewById(R.id.stop_btn);
                    FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
                    bottombar = (LinearLayout) (findViewById(R.id.bottombar));

                    if(paused) {
                        paused = false;
                        pausebtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
                    }
                    stopRecording();
                    stopNotification();
                    ObjectAnimator sanim = ObjectAnimator.ofFloat(bottombar, "translationY", parent_view.getY(), parent_view.getY()+bottombar.getHeight());
                    sanim.setInterpolator(new FastOutLinearInInterpolator());
                    sanim.setDuration(250);
                    sanim.start();
                    sanim.addListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            bottombar.setVisibility(View.INVISIBLE);
                            pausebtn.setEnabled(false);
                            stopbtn.setEnabled(false);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    });

                    Animation anim = growIn();
                    FAButton.setVisibility(View.VISIBLE);
                    FAButton.setEnabled(true);
                    anim.setStartOffset(300);
                    anim.setDuration(200);
                    anim.setFillEnabled(false);
                    anim.setFillAfter(false);
                    FAButton.startAnimation(anim);
                    holdRecording = false;
                    log("STOPbtn pressed");
                    if (holdToRecord) {
                        setButtons();
                    }
                }

            }
        };


        View.OnLongClickListener long_button_listener = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if(view.getId() == R.id.recbutton) {
                    if(holdToRecord) {
                        if(!recording) {
                            if(isFingerDown()) {
                                log("Record while holding");
                                recording = true;
                                holdRecording = true;
                                startRecording();
                                createNotification(getApplicationContext());
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        };


        final View.OnTouchListener touch_button_listener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {

                if(view.getId() == R.id.pause_btn || view.getId() == R.id.stop_btn) {
                    if (motionEvent.getAction() == (MotionEvent.ACTION_DOWN)) {
                        view.setBackgroundColor(color_primary);
                    } else if (motionEvent.getAction() == (MotionEvent.ACTION_UP)) {
                        view.setBackgroundColor(color_primary_dark);
                    }
                    return false;
                }
                if(view.getId() == R.id.recbutton) {
                    if(holdToRecord && !recording && !isFingerDown() && motionEvent.getAction() == (MotionEvent.ACTION_DOWN)) {
                        fingerDown = true;
                    }
                    if(holdToRecord && motionEvent.getAction() == (MotionEvent.ACTION_UP)) {
                        fingerDown = false;
                        log("Check if record onClick or stop");
                        if(recording && holdRecording) {
                            log("Stop on lift");
                            stopRecording();
                            stopNotification();
                            recording = false;
                            holdRecording = false;
                        }
                    }
                }
                return false;
            }
        };

        if(holdToRecord) {
            log("holdToRecord: true");
            FAButton.setOnTouchListener(touch_button_listener); //OnTouch to enable hold to record
            FAButton.setOnLongClickListener(long_button_listener);
            FAButton.setHapticFeedbackEnabled(false);
        } else {
            log("holdToRecord: false");
            //Deactivate FAB since not used.
            FAButton.setOnTouchListener(null);
            FAButton.setOnLongClickListener(null);
        }

        FAButton.setOnClickListener(buttons_listener);
        colourbtn.setOnClickListener(buttons_listener); //To choose colours
        folderbtn.setOnClickListener(buttons_listener);
        pausebtn.setOnTouchListener(touch_button_listener);
        stopbtn.setOnTouchListener(touch_button_listener);
        pausebtn.setOnClickListener(buttons_listener); //To pause recording
        stopbtn.setOnClickListener(buttons_listener); //To sotp recording

    }

    private boolean isFingerDown() {
        return fingerDown;
    }

    private void initializeLayouts() {
        pausebtn = (ImageButton) findViewById(R.id.pause_btn);
        parent_view = (RelativeLayout) findViewById(R.id.parent);
        stopbtn = (ImageButton) findViewById(R.id.stop_btn);
        colourbtn = (ImageButton) findViewById(R.id.palette);
        FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
        bottombar = (LinearLayout) (findViewById(R.id.bottombar));
        timer = (TextView) findViewById(R.id.timeTV);
        statusTV = (TextView) findViewById(R.id.statusTV);
        toolbar = (Toolbar) findViewById(R.id.toolbar);
        folderbtn = (ImageButton) findViewById(R.id.folder);
        bglayout = (LinearLayout) findViewById(R.id.bglayout);
        recyclerView = (FixedRecyclerView) findViewById(R.id.recycler_view);
    }

    private void stopPlaying() {
        if(gHolder != null) {
            if(sliderVisible) {
                toggleSliderVisibility(false, gHolder);
            }
            gHolder = null;
        }
        mPlayer.stop();
        mPlayer.reset();
        mPlayer.release();
        mPlayer = null;
    }

    private void showSnack(String text, boolean length_short) {
        if(length_short) {
            Snackbar bar = Snackbar.make(parent_view, text, Snackbar.LENGTH_SHORT);
            bar.show();
        } else {
            Snackbar bar = Snackbar.make(parent_view, text, Snackbar.LENGTH_LONG);
            bar.show();
        }
    }

    public String timerText(int duration) {
        int minutes = duration/60;
        int seconds = duration-(minutes*60);
        String time = "";
        if(minutes < 10) {
            time += "0";
        }
        time += minutes;
        time += ":";
        if(seconds < 10) {
            time += "0";
        }
        time += seconds;
        return time;
    }

    private void playRecording(String source) {
        final Slider slider = gHolder.slider;
        final TextView timer = gHolder.textView_timer;
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(source);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(mPlayer.getDuration()/1000 > 1) {
            toggleSliderVisibility(true, gHolder);
        }
        mPlayer.start();
        if(slider != null) {
            slider.setValueRange(0, (mPlayer.getDuration() / 1000), true);
            slider.setValue(0, true);
        }

        final Runnable updateSlider = new Runnable() {

            MediaPlayer current = mPlayer;
            @Override
            public void run() {
                if(mPlayer == null || current != mPlayer) {
                    Thread.currentThread().interrupt();
                } else {
                    if(sliderVisible) {
                        assert slider != null;
                        slider.setValue((mPlayer.getCurrentPosition() / 1000), true);
                        timer.setText(timerText(mPlayer.getCurrentPosition()/1000));
                    }
                }
            }
        };

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while(!Thread.currentThread().isInterrupted()) {
                    if (slider != null && mPlayer != null && !Thread.currentThread().isInterrupted()) {
                        runOnUiThread(updateSlider);
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
        try {
            assert slider != null;
            slider.setOnPositionChangeListener(new Slider.OnPositionChangeListener() {
                @Override
                public void onPositionChanged(Slider slider, boolean b, float v, float v1, int i, int i1) {
                    if (b) {
                        mPlayer.seekTo(i1*1000);
                        slider.setValue(i1, true);
                    }

                }
            });
        } catch (Exception ignored) {

        }
        mPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if(mPlayer != null) {
                    //Thread.currentThread().interrupt();
                    stopPlaying();
                }
                sessionid = -1;
            }
        });
        Snackbar bar = Snackbar.make(parent_view, R.string.text_playback_started, Snackbar.LENGTH_SHORT);
        bar.show();
    }

    private void log(String logtoprint) {
        boolean logEnabled = true;
        if (logEnabled) {
            Log.e("Main", logtoprint);
        }
    }

    private void loadFiles(int type) {
        recordings.clear();
        File dir = new File(getFolderPath());
        File paths[] = dir.listFiles();
        File nomedia = new File(getFolderPath()+"/.nomedia");
        if(!nomedia.exists()) {
            try {
                nomedia.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        int position = -1;
        if(paths != null) {
            for (File path : paths) {
                if(!getFileExt(path.getAbsolutePath()).equalsIgnoreCase("wav") && !getFileExt(path.getAbsolutePath()).equalsIgnoreCase("m4a")) continue;
                MediaMetadataRetriever metaRetriever = new MediaMetadataRetriever();
                FileInputStream fileInputStream = null;
                try {
                    fileInputStream = new FileInputStream(path.getAbsolutePath());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                try {
                    assert fileInputStream != null;
                    metaRetriever.setDataSource(fileInputStream.getFD());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                String duration = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String fname = path.getName();
                String date;
                String time;
                if (getFileExt(path.getName()).equals("wav") || getFileExt(path.getName()).equals("m4a")) {
                    Date d = new Date(path.lastModified());
                    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                    date = formatter.format(d);
                    formatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    time = formatter.format(d);
                } else {
                    date = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
                    SimpleDateFormat formatter = new SimpleDateFormat("h:mm a", Locale.getDefault());
                    time = formatter.format(path.lastModified());
                }
                RecordedFile file = new RecordedFile(fname, duration, date, time, path.getAbsolutePath());
                position++;
                if (type == 3) {
                    //recyclerAdapter.notifyItemInserted(position);
                    notifyItemAddedWithHandler(position, file);
                } else {
                    recordings.add(file);
                }
            }
        }
    }

    @Override
    public void onSelectDirectory(String s) {
        setFolder(s + "/");
        folderPath = s + "/";
        updateFiles(1);
        mDialogFragment.dismiss();
    }

    @Override
    public void onCancelChooser() {
        mDialogFragment.dismiss();
    }


    class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equalsIgnoreCase("elcarecorder.refresh")) {
                log("Refresh list broadcast received");
                if(getActive()) {
                    updateFiles(2);
                    unregisterReceiver(receiver);
                    receiver = null;
                }
            }
        }
    }

    class PlayClickListener implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            int position = recyclerView.getChildAdapterPosition(view);
            RecordedFile recording = recordings.get(position);
            String source = recording.path;
            RecyclerAdapter.ViewHolder viewHolder = (RecyclerAdapter.ViewHolder) view.getTag();
            if(sessionid != position) {
                if(mPlayer != null) {
                    stopPlaying();
                    if(gHolder != null) {
                        toggleSliderVisibility(false, gHolder);
                    }
                    sessionid = -1;
                }
                gHolder = viewHolder;
                playRecording(source);
                sessionid = position;
            } else {
                stopPlaying();
                sessionid = -1;
                showSnack(getString(R.string.text_playback_stopped), true);
            }
        }
    }

    class RecyclerAdapter extends RecyclerView.Adapter <RecyclerAdapter.ViewHolder> {

        private int contextPosition;

        public RecyclerAdapter() {

        }

        public void setContextPosition(int position) {
            this.contextPosition = position;
        }

        public int getContextPosition() {
            return contextPosition;
        }

        @Override
        public void onViewRecycled(ViewHolder holder) {
            holder.itemView.setOnLongClickListener(null);
            super.onViewRecycled(holder);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            final View itemView = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.filelist, null);
            ViewHolder holder = new ViewHolder(itemView);
            itemView.setOnClickListener(playClickListener);
            itemView.setTag(holder);
            itemView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu contextMenu, View view, ContextMenu.ContextMenuInfo contextMenuInfo) {
                    MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.tap_context, contextMenu);
                }
            });
            return holder;
        }

        @Override
        public void onBindViewHolder(final ViewHolder viewHolder, int i) {
            RecordedFile recording = recordings.get(i);
            viewHolder.fnTV.setText(recording.filename);
            viewHolder.lTV.setText(recording.duration);
            viewHolder.dTV.setText(recording.date);
            viewHolder.tTV.setText(recording.time);
            viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    setContextPosition(viewHolder.getAdapterPosition());
                    return false;
                }
            });
            toggleSliderVisibility(false, viewHolder);
            if(i == sessionid && i != -1) {
                toggleSliderVisibility(true, viewHolder);
            }
        }

        @Override
        public int getItemCount() {
            return recordings.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            TextView fnTV;
            TextView dTV;
            TextView lTV;
            TextView tTV;
            TextView textView_timer;
            Slider slider;
            TextView stopView;

            public ViewHolder(View itemView) {
                super(itemView);
                fnTV = (TextView) itemView.findViewById(R.id.fnLVTV);
                dTV = (TextView) itemView.findViewById(R.id.dLVTV);
                lTV = (TextView) itemView.findViewById(R.id.lLVTV);
                tTV = (TextView) itemView.findViewById(R.id.tLVTV);
                slider = (Slider) itemView.findViewById(R.id.slider);
                stopView = (TextView) itemView.findViewById(R.id.stop_playback);
                textView_timer = (TextView) itemView.findViewById(R.id.textView_timer);
            }
        }
    }

    public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
        private int space;

        public SpacesItemDecoration(int space) {
            this.space = space;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            //outRect.bottom = space;
            //outRect.left = space*2;
            //outRect.right = space*2;
            if(parent.getChildAdapterPosition(view) == 0)
                outRect.top = space * 2;
        }
    }

    class ListUpdate extends AsyncTask<Void, Void, Void> {

        int type = 1;

        public ListUpdate(int type) {
            this.type = type;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            loadFiles(type);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if(type == 1) {  // App resumed
                recyclerAdapter.notifyDataSetChanged();
            } else if(type == 2) { // Recording added via refresh
                recyclerAdapter.notifyDataSetChanged();
            } else if(type == 3) { // Recordings added on app start
                //recyclerAdapter.notifyItemRangeInserted(0, recordings.size());
            }
        }
    }

    private void toggleSliderVisibility(boolean visible, RecyclerAdapter.ViewHolder holder) {
        if(holder == null) return;
        if(!visible) {
            sliderVisible = false;
            holder.slider.setVisibility(View.GONE);
            holder.stopView.setVisibility(View.GONE);
            holder.textView_timer.setVisibility(View.GONE);
            holder.fnTV.setVisibility(View.VISIBLE);
            holder.lTV.setVisibility(View.VISIBLE);
            holder.dTV.setVisibility(View.VISIBLE);
            holder.tTV.setVisibility(View.VISIBLE);
        } else {
            sliderVisible = true;
            holder.fnTV.setVisibility(View.GONE);
            holder.lTV.setVisibility(View.GONE);
            holder.dTV.setVisibility(View.GONE);
            holder.tTV.setVisibility(View.GONE);
            holder.stopView.setVisibility(View.VISIBLE);
            holder.slider.setVisibility(View.VISIBLE);
            holder.textView_timer.setVisibility(View.VISIBLE);
        }
    }

    public void notifyItemAddedWithHandler(final int position, final RecordedFile file) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                recordings.add(file);
                recyclerAdapter.notifyItemInserted(position);
            }
        });
    }

    private String getFolderPath() {
        return folderPath;
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

    private void showRecordLayout() {
        //recording = isMyServiceRunning(RService.class);
        if(!recording) {
            recording = true;
            pausebtn = (ImageButton) findViewById(R.id.pause_btn);
            stopbtn = (ImageButton) findViewById(R.id.stop_btn);
            FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
            bottombar = (LinearLayout) (findViewById(R.id.bottombar));

            Animation anim = shrinkOut();
            anim.setFillEnabled(false);
            anim.setFillAfter(false);
            FAButton.startAnimation(anim);
            FAButton.setVisibility(View.GONE);
            FAButton.setEnabled(false);

            bottombar.setVisibility(View.VISIBLE);
            if (recordingMode == 2) {
                pausebtn.setEnabled(true);
            } else {
                pausebtn.setEnabled(false);
            }
            stopbtn.setEnabled(true);
            ObjectAnimator anima = ObjectAnimator.ofFloat(bottombar, "translationY", parent_view.getY() + bottombar.getHeight(), parent_view.getY());
            anima.setInterpolator(new FastOutLinearInInterpolator());
            anima.setDuration(250);
            anima.setStartDelay(250);
            anima.start();
            createNotification(getApplicationContext());
            if(receiver == null) {
                receiver = new Receiver();
                IntentFilter filter = new IntentFilter();
                filter.addAction("elcarecorder.refresh");
                registerReceiver(receiver, filter);
            }
            log("Recording due to Intent");
        }
    }

    private void recordFromWidget() {
        recording = true;
        startRecording();
        pausebtn = (ImageButton) findViewById(R.id.pause_btn);
        stopbtn = (ImageButton) findViewById(R.id.stop_btn);
        FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
        bottombar = (LinearLayout) (findViewById(R.id.bottombar));

        Animation anim = shrinkOut();
        anim.setFillEnabled(false);
        anim.setFillAfter(false);
        FAButton.startAnimation(anim);
        FAButton.setVisibility(View.GONE);
        FAButton.setEnabled(false);

        bottombar.setVisibility(View.VISIBLE);
        if(recordingMode ==2 ) {
            pausebtn.setEnabled(true);
        } else {
            pausebtn.setEnabled(false);
        }
        stopbtn.setEnabled(true);
        ObjectAnimator anima = ObjectAnimator.ofFloat(bottombar,"translationY",parent_view.getY()+bottombar.getHeight(),parent_view.getY());
        anima.setInterpolator(new FastOutLinearInInterpolator());
        anima.setDuration(250);
        anima.setStartDelay(250);
        anima.start();
        createNotification(getApplicationContext());
        log("Recording due to Intent");
    }

    private void stopFromWidget() {
        recording = false;
        pausebtn = (ImageButton) findViewById(R.id.pause_btn);
        stopbtn = (ImageButton) findViewById(R.id.stop_btn);
        FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
        bottombar = (LinearLayout) (findViewById(R.id.bottombar));

        if(paused) {
            paused = false;
            pausebtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
        }
        stopRecording();
        stopNotification();
        ObjectAnimator sanim = ObjectAnimator.ofFloat(bottombar, "translationY", parent_view.getY(), parent_view.getY() + bottombar.getHeight());
        sanim.setInterpolator(new FastOutLinearInInterpolator());
        sanim.setDuration(250);
        sanim.start();
        sanim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                bottombar.setVisibility(View.INVISIBLE);
                pausebtn.setEnabled(false);
                stopbtn.setEnabled(false);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        Animation anim = growIn();
        FAButton.setVisibility(View.VISIBLE);
        FAButton.setEnabled(true);
        anim.setStartOffset(300);
        anim.setDuration(200);
        anim.setFillEnabled(false);
        anim.setFillAfter(false);
        FAButton.startAnimation(anim);
        log("Stop from intent");
    }

    private AlertDialog QualitySettings() {
        AlertDialog.Builder builder = new AlertDialog.Builder(main.this, R.style.Theme_AppCompat_Light_Dialog_Alert);
        builder.setTitle(getString(R.string.title_quality_settings));
        final View view = LayoutInflater.from(main.this).inflate(R.layout.qualitysettings, null);
        builder.setView(view);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(main.this, R.array.frequency_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spinner = (Spinner) view.findViewById(R.id.spinner_frequency);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(spinner_item_listener);
        spinner.setSelection(adapter.getPosition(Integer.toString(frequency)));
        adapter = ArrayAdapter.createFromResource(main.this, R.array.format_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner = (Spinner) view.findViewById(R.id.spinner_format);
        spinner.setAdapter(adapter);
        if(recordingMode == 1) {
            spinner.setSelection(1);
        } else spinner.setSelection(0);
        spinner.setOnItemSelectedListener(spinner_item_listener);
        builder.setNegativeButton(getString(R.string.button_quality_settings_close), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        return builder.create();
    }



    private void showFeedbackDialog() {
        AlertDialog dialog = new AlertDialog.Builder(main.this, R.style.Theme_AppCompat_Light_Dialog_Alert)
                .setPositiveButton("OKAY", null)
                .create();
        dialog.setMessage(getString(R.string.text_feedback_prompt));
        dialog.show();
    }

    public void stopButtonPress() {
        recording = false;
        pausebtn = (ImageButton) findViewById(R.id.pause_btn);
        stopbtn = (ImageButton) findViewById(R.id.stop_btn);
        FAButton = (FloatingActionButton) findViewById(R.id.recbutton);
        bottombar = (LinearLayout) (findViewById(R.id.bottombar));

        if(paused) {
            paused = false;
            pausebtn.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_24dp));
        }
        stopRecording();
        stopNotification();
        ObjectAnimator sanim = ObjectAnimator.ofFloat(bottombar, "translationY", parent_view.getY(), parent_view.getY()+bottombar.getHeight());
        sanim.setInterpolator(new FastOutLinearInInterpolator());
        sanim.setDuration(250);
        sanim.start();
        sanim.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animator) {

            }

            @Override
            public void onAnimationEnd(Animator animator) {
                bottombar.setVisibility(View.INVISIBLE);
                pausebtn.setEnabled(false);
                stopbtn.setEnabled(false);
            }

            @Override
            public void onAnimationCancel(Animator animator) {

            }

            @Override
            public void onAnimationRepeat(Animator animator) {

            }
        });

        Animation anim = growIn();
        FAButton.setVisibility(View.VISIBLE);
        FAButton.setEnabled(true);
        anim.setStartOffset(300);
        anim.setDuration(200);
        anim.setFillEnabled(false);
        anim.setFillAfter(false);
        FAButton.startAnimation(anim);
        holdRecording = false;
        log("STOPbtn pressed");
        if (holdToRecord) {
            setButtons();
        }
    }


    @TargetApi(Build.VERSION_CODES.M)
    public void checkApplicationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            List<String> list = new ArrayList<String>();
            int perms = -1;
            if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
                //permissions[++perms] =
                list.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                //requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
                list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                //requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 3);
                list.add(Manifest.permission.RECORD_AUDIO);
            }
            if(list.size() > 0) {
                String[] permissions = list.toArray(new String[list.size()]);
                requestPermissions(permissions, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(recordingid == 0) {
            showFeedbackDialog();
        }
    }
}



/* --------- TO DO -----------

-> Layout: add proper layout for tablets
-> Android wear support for stealth recording (record from widget)

*/

/*
Changelog:

- Added touch and hold to record
- Added settings menu
- Added slider to seek audio playback

*/