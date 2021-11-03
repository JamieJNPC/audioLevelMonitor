package com.example.audiolevelmonitor2;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;

import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.example.audiolevelmonitor2.databinding.ActivityMainBinding;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private MediaRecorder recorder;
    private String fileName = "audiotest.mp3";
    private String csvFileName = "audiotest.csv";
    private ParcelFileDescriptor file;
    private RecordButton recordButton = null;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO};
    private double db = 0;
    private boolean isRecording = false;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) finish();

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        LinearLayout ll = new LinearLayout(this);
        recordButton = new RecordButton(this);
        ll.addView(recordButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        setContentView(ll);
    }

    private void startRecording() throws FileNotFoundException {
        try {
            ContentValues values = new ContentValues(4);
            values.put(MediaStore.Audio.Media.DISPLAY_NAME, fileName);
            //values.put(MediaStore.Audio.Media.TITLE, fileName);
            values.put(MediaStore.Audio.Media.DATE_ADDED, (int) (System.currentTimeMillis() / 1000));
            values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
            values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings/");

            Uri audiouri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
            file = getContentResolver().openFileDescriptor(audiouri, "w");

            recorder = new MediaRecorder(this);
            recorder.setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(file.getFileDescriptor());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            recorder.prepare();
            recorder.start();
            new Thread(recordAudioLevel).start();
        } catch(Exception e) {System.out.println(e);}
    }

    private void stopRecording() {
        recorder.stop();
        recorder.reset();
        recorder.release();
        getContentResolver().delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null);
        //recorder = null;
    }

    private void onRecord(boolean start) {
        try {
            isRecording = start;
            if (start) {
                startRecording();
            } else {
                stopRecording();
            }
        } catch(Exception e){}
    }

    private final Runnable recordAudioLevel = new Runnable() {
        public void run() {
            try {
                ContentValues values = new ContentValues(4);
                values.put(MediaStore.Audio.Media.DISPLAY_NAME, csvFileName);
                values.put(MediaStore.Audio.Media.DATE_ADDED, (int) (System.currentTimeMillis() / 1000));
                values.put(MediaStore.Audio.Media.MIME_TYPE, "text/csv");
                values.put(MediaStore.Audio.Media.RELATIVE_PATH, "");

                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                file = getContentResolver().openFileDescriptor(uri, "w");
                FileWriter fw = new FileWriter(file.getFileDescriptor());
                BufferedWriter bw = new BufferedWriter(fw);
                bw.write("Timestamp,Decibels\n");
                while (isRecording) {
                    db = 20 * Math.log10(recorder.getMaxAmplitude() / 2700.0);

                    Date c = Calendar.getInstance().getTime();
                    SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss:SS", Locale.getDefault());
                    String formattedDate = df.format(c);

                    bw.write(formattedDate + "," + db + "\n");
                    bw.flush();
                    System.out.println(formattedDate + ", " + db);
                    Thread.sleep(1000);
                    uploadDataNode((double) db);
                }
                bw.close();
                fw.close();
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    };

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (recorder != null) {
            recorder.release();
            recorder = null;
        }
    }

    public void uploadDataNode(double noiseLevel) {
        /*
        if(noiseLevel > -1000) {
            long unixTime = System.currentTimeMillis() / 1000L;
            Noise item = Noise.builder()
                    .noiselevel(noiseLevel)
                    .timestamp(new Temporal.Timestamp(unixTime, TimeUnit.SECONDS))
                    .build();
            Amplify.DataStore.save(
                    item,
                    success -> Log.i("Amplify", "Saved item: " + success.item().getId()),
                    error -> Log.e("Amplify", "Could not save item to DataStore", error)
            );
            System.out.println(unixTime + " " + noiseLevel);
        }
         */
    }

    class RecordButton extends androidx.appcompat.widget.AppCompatButton {
        boolean mStartRecording = true;

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                onRecord(mStartRecording);
                if (mStartRecording) {
                    setText("Stop recording");
                } else {
                    setText("Start recording");
                }
                mStartRecording = !mStartRecording;
            }
        };

        public RecordButton(Context ctx) {
            super(ctx);
            setText("Start recording");
            setOnClickListener(clicker);
        }
    }
}