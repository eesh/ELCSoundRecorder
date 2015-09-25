package elcapps.elcasoundrecorder;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class RService extends Service {

    private boolean isRecording;
    private boolean intentreceived;
    private boolean paused;
    private boolean appStart;
    private boolean logEnabled = true;
    private Thread recordingThread;
    private AudioRecord recorder;
    private Receiver receiver;
    private int SAMPLE_RATE;
    private String fileName;
    private String pcmFile;
    private String folderPath;
    private int CHANNELS;
    private String extention;
    private int RECORDER_ENCODING;
    private int bufferSize;
    private int recordingMode;
    private int fileNumber;
    private MediaRecorder mediaRecorder;

    public RService() {
        mediaRecorder = null;
        appStart = true;
        intentreceived = false;
        paused = false;
        recordingThread = null;
        recorder = null;
        fileName = null;
        pcmFile = null;
        extention = ".wav";
        CHANNELS = AudioFormat.CHANNEL_IN_MONO;
        RECORDER_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        log("Service object instantiated");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("onStartCommand()");
        if (!appStart) {
            log("stopSelf() called at onStartCommand()");
            stopSelf();
        }
        setupReceiver();
        recorderParameters();
        loadPreferenceData();
        startRecording();
        return super.onStartCommand(intent, flags, startId);
    }


    private void startRecording() {
        if (!appStart) {
            log("stopSelf() called at startRecording()");
            stopSelf();
        }
        log("startRecording() recordingMode:"+ recordingMode);
        if(recordingMode == 2) {
            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNELS, RECORDER_ENCODING, bufferSize);
            isRecording = true;
            recorder.startRecording();
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    writeToFile();
                }
            } , "AudioRecorder Thread");
            recordingThread.start();
            log("Recording started in mode 2 (wav)");
        }
        if(recordingMode == 1) {
            fileName = getFolderPath() + "recording " + fileNumber + ".m4a";
            log(fileName);
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setAudioSamplingRate(SAMPLE_RATE);
            //mediaRecorder.setAudioChannels(1);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(16);
            mediaRecorder.setOutputFile(fileName);
            try {
                mediaRecorder.prepare();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mediaRecorder.start();
            log("mediaRecorder recording");
        }
    }


    private void writeToFile() {

        pcmFile = getFolderPath() + "recording " + fileNumber + ".pcm";
        fileName = getFolderPath() + "recording " + fileNumber + ".wav";
        byte sData[] = new byte[bufferSize];
        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(new FileOutputStream(pcmFile, true));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        while (isRecording) {
            int gain = 1;
            int result = recorder.read(sData, 0, bufferSize);
            try {
                if(result > 0) {
                    for (int i = 0; i < result; ++i) {
                        long temp;
                        if((long)sData[i] != 0) {
                            temp = (long) sData[i] * gain;
                        } else {
                            temp = (long) sData[i];
                        }
                        if (temp > 32767) { temp = 32767;}
                        if (temp <- 32768) { temp = -32768;}
                        sData[i] = (byte)temp;
                    }
                    assert os != null;
                    os.write(sData, 0, result);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        try {
            assert os != null;
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if(!paused) {
            log("Copying wav file");
            copyWaveFile(pcmFile, fileName);
        }

    }

    private void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen;
        long longSampleRate = SAMPLE_RATE;
        int channels = 1;
        long byteRate = 16 * SAMPLE_RATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36; // + 36;

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }

            in.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        FileObserver fileObserver = new FileObserver(pcmFile, FileObserver.DELETE_SELF) {
            @Override
            public void onEvent(int i, String s) {
                if(getActive()) {
                    log("Sending broadcast " + i);
                    sendBroadcast(new Intent("elcarecorder.refresh"));
                }
                log("stopSelf() called at fileObserver onEvent()");
                stopSelf();
            }
        };
        fileObserver.startWatching();
        File pcm = new File(pcmFile);
        if(pcm.delete()) {
            log("pass");
        }
        else {
                log("fail");
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        log("Writing wav header: " + out.getFD().toString());
        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = 16;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }


    private void loadPreferenceData() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        String defaultPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/ELCArecorder/";
        folderPath = pref.getString(getString(R.string.folderPath), defaultPath);
        recordingMode = 1;//pref.getInt(getString(R.string.mode), 2);
        fileNumber = pref.getInt(getString(R.string.lastclipid),0);
        fileNumber++;
        File file = new File(getFolderPath() + "recording " + fileNumber + ".wav");
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(getString(R.string.lastclipid), fileNumber);
        editor.apply();
        log("Preferences loaded Number: " + fileNumber);
    }


    private void recorderParameters() {
        SAMPLE_RATE = getFrequency();
        bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNELS, RECORDER_ENCODING);
        log("Recording parameters set");
    }


    private void setupReceiver() {
        receiver = new Receiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("elcarecorder.pause");
        intentFilter.addAction("elcarecorder.continue");
        intentFilter.addAction("elcarecorder.stop");
        this.registerReceiver(receiver, intentFilter);
    }


    private void pause() {
        paused = true;
        isRecording = false;
        recorder.stop();
        recorder.release();
        recorder = null;
        recordingThread = null;
        log("Paused");
    }

    private void stop() {
        if(paused) {
            paused = false;
            startRecording();
        }
        isRecording = false;
        if(recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        recordingThread = null;
        appStart = false;
        if(mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            sendBroadcast(new Intent("elcarecorder.refresh"));
            stopSelf();
        }
        log("Stopped");
    }

    private void continueRecording() {
        paused = false;
        isRecording = true;
        startRecording();
        log("Recording resumed");
    }

    public boolean getActive() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getBoolean(getString(R.string.activitystatus),true);
    }

    public int getFrequency() {
        SharedPreferences pref = getSharedPreferences("recordingPrefs", Context.MODE_PRIVATE);
        return pref.getInt(getString(R.string.frequency), 44100);
    }

    class Receiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equalsIgnoreCase("elcarecorder.pause")) {
                log("Pause intent received");
                pause();
            }
            if (intent.getAction().equalsIgnoreCase("elcarecorder.stop")) {
                log("Stop intent received");
                if(!intentreceived) {
                    stop();
                    intentreceived = true;
                }
            }
            if (intent.getAction().equalsIgnoreCase("elcarecorder.continue")) {
                log("Continue intent received");
                continueRecording();
            }
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(receiver);
        receiver = null;
        if(!intentreceived && isRecording) {
            log("onDestroy");
            stop();
        }
        log("Service destroyed");
        super.onDestroy();
    }

    private void log(String logtoprint) {
        if (logEnabled) {
            Log.e("RService", logtoprint);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }


    public String getFolderPath() {
        return folderPath;
    }
}
