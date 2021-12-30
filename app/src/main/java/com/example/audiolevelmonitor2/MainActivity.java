package com.example.audiolevelmonitor2;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import com.example.audiolevelmonitor2.databinding.ActivityMainBinding;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

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
    private static final int REQUEST_FINE_LOCATION = 101;
    private boolean permissionToRecordAccepted = false;
    private String [] permissions = {Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION};
    private double db = 0;
    private boolean isRecording = false;
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case REQUEST_RECORD_AUDIO_PERMISSION:
            case REQUEST_FINE_LOCATION:
                permissionToRecordAccepted  = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;

        }
        if (!permissionToRecordAccepted ) finish();

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this, permissions, REQUEST_FINE_LOCATION);
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
        @SuppressLint("MissingPermission")
        public void run() {
            try {
                while (isRecording) {
                    LocationManager lm = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
                    Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                    double longitude, latitude;
                    if(location != null) {
                        latitude = location.getLatitude();
                        longitude = location.getLongitude();
                    }
                    else {
                        latitude = 0.0;
                        longitude = 0.0;
                    }
                    db = 20 * Math.log10(recorder.getMaxAmplitude() / 2700.0);
                    Thread.sleep(1000);
                    uploadDataNode((double) db, longitude, latitude);
                }
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

    public void uploadDataNode(double noiseLevel, double longitude, double latitude) {
        if(noiseLevel > -1000 && longitude != 0.0) {
            long unixTime = System.currentTimeMillis() / 1000L;
            System.out.println(unixTime + " " + noiseLevel + " " + longitude + " " + latitude);
            FirebaseDatabase database = FirebaseDatabase.getInstance("https://noiselevelmonitor-4b7eb-default-rtdb.europe-west1.firebasedatabase.app/");
            DatabaseReference myRef = database.getReference("noise/" + unixTime);
            myRef.setValue(noiseLevel + " " + longitude + " " + latitude);
        }

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