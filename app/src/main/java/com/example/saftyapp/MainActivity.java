package com.example.saftyapp;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.button.MaterialButton;
import android.widget.Switch;

import java.io.File;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    
    private View btnPanic;
    private MaterialButton btnCancel;
    private TextView tvStatus, tvTimer;
    private EditText etPhone, etMessage;
    private ProgressBar panicProgress;
    private Switch switchSiren, switchFlashlight;

    private Handler handler = new Handler();
    private Runnable emergencyRunnable;
    private int countdown = 10;
    private boolean isEmergencyActive = false;

    private MediaRecorder recorder;
    private String audioPath;
    private FusedLocationProviderClient fusedLocationClient;
    private NotificationManager notificationManager;
    private CameraManager cameraManager;
    private String cameraId;
    private ToneGenerator toneGenerator;
    private Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI
        btnPanic = findViewById(R.id.btnPanicContainer);
        btnCancel = findViewById(R.id.btnCancel);
        tvStatus = findViewById(R.id.tvStatus);
        tvTimer = findViewById(R.id.tvTimer);
        etPhone = findViewById(R.id.etPhone);
        etMessage = findViewById(R.id.etMessage);
        panicProgress = findViewById(R.id.panic_progress);
        switchSiren = findViewById(R.id.switchSiren);
        switchFlashlight = findViewById(R.id.switchFlashlight);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        try {
            if (cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        createNotificationChannel();
        checkPermissions();

        btnPanic.setOnClickListener(v -> {
            if (!isEmergencyActive) {
                triggerEmergency();
            }
        });

        btnCancel.setOnClickListener(v -> stopEmergency());

        SessionManager sessionManager = new SessionManager(this);
        etPhone.setText(sessionManager.getPhoneNumber());
        etMessage.setText(sessionManager.getCustomMessage());
    }

    @Override
    protected void onPause() {
        super.onPause();
        SessionManager sessionManager = new SessionManager(this);
        if (etPhone != null && etMessage != null) {
            sessionManager.saveEmergencyDetails(etPhone.getText().toString(), etMessage.getText().toString());
        }
    }

    private void checkPermissions() {
        java.util.List<String> permissionList = new java.util.ArrayList<>();
        permissionList.add(Manifest.permission.SEND_SMS);
        permissionList.add(Manifest.permission.RECORD_AUDIO);
        permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionList.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        String[] permissions = permissionList.toArray(new String[0]);

        if (!hasPermissions(permissions)) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    private boolean hasPermissions(String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void triggerEmergency() {
        isEmergencyActive = true;
        tvStatus.setText("EMERGENCY ACTIVE");
        tvStatus.setTextColor(getColor(R.color.panic_red));
        btnCancel.setVisibility(View.VISIBLE);
        tvTimer.setVisibility(View.VISIBLE);
        panicProgress.setVisibility(View.VISIBLE);
        
        showNotification("Guardian SOS", "Emergency protocol initiated. Sending alerts in 10s.");
        startRecording();
        vibrate(500);
        
        if (switchSiren.isChecked()) startSiren();
        if (switchFlashlight.isChecked()) toggleFlashlight(true);

        countdown = 10;
        panicProgress.setProgress(0);
        
        emergencyRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdown > 0) {
                    tvTimer.setText(String.valueOf(countdown));
                    panicProgress.setProgress((10 - countdown + 1) * 10);
                    countdown--;
                    handler.postDelayed(this, 1000);
                } else {
                    sendFinalAlert();
                }
            }
        };
        handler.post(emergencyRunnable);
    }

    private void stopEmergency() {
        isEmergencyActive = false;
        handler.removeCallbacks(emergencyRunnable);
        stopRecording();
        stopSiren();
        toggleFlashlight(false);
        
        tvStatus.setText("System Protected");
        tvStatus.setTextColor(getColor(R.color.accent_green));
        tvTimer.setVisibility(View.INVISIBLE);
        btnCancel.setVisibility(View.INVISIBLE);
        panicProgress.setVisibility(View.GONE);
        
        Toast.makeText(this, "Emergency Cancelled", Toast.LENGTH_SHORT).show();
        notificationManager.cancel(1);
    }

    private void startSiren() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 10000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopSiren() {
        if (toneGenerator != null) {
            toneGenerator.stopTone();
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void toggleFlashlight(boolean on) {
        if (cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId, on);
            } catch (CameraAccessException | IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void vibrate(int ms) {
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }

    private void sendFinalAlert() {
        tvTimer.setText("SENT");
        tvStatus.setText("ALERTS DISPATCHED");
        vibrate(1000);
        
        String customMsg = etMessage.getText().toString();
        String phoneNumber = etPhone.getText().toString().trim();
        
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Emergency phone number is not set!", Toast.LENGTH_LONG).show();
            return;
        }
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        sendLocationSMS(phoneNumber, customMsg, location);
                    } else {
                        // Fallback to last known location
                        fusedLocationClient.getLastLocation().addOnSuccessListener(this, lastLoc -> {
                            if (lastLoc != null) {
                                sendLocationSMS(phoneNumber, customMsg, lastLoc);
                            } else {
                                sendSMS(phoneNumber, customMsg + "\n(Location could not be determined)");
                            }
                        }).addOnFailureListener(this, e -> {
                            sendSMS(phoneNumber, customMsg + "\n(Location failed)");
                        });
                    }
                })
                .addOnFailureListener(this, e -> {
                    sendSMS(phoneNumber, customMsg + "\n(Location failed: " + e.getMessage() + ")");
                });
        } else {
            sendSMS(phoneNumber, customMsg + " (Location access denied)");
        }
    }

    private void sendLocationSMS(String phoneNumber, String customMsg, Location location) {
        String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + location.getLatitude() + "," + location.getLongitude();
        String message = customMsg + "\nLocation: " + mapsUrl;
        sendSMS(phoneNumber, message);
    }

    private void sendSMS(String phoneNumber, String message) {
        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            }
            Toast.makeText(this, "SOS SMS Sent!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "SMS Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startRecording() {
        try {
            File audioFile = new File(getExternalFilesDir(null), "emergency_record.m4a");
            audioPath = audioFile.getAbsolutePath();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recorder = new MediaRecorder(this);
            } else {
                recorder = new MediaRecorder();
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioPath);
            recorder.prepare();
            recorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopRecording() {
        if (recorder != null) {
            try {
                recorder.stop();
                recorder.release();
                recorder = null;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("emergency_channel", "SOS Alerts", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String content) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "emergency_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        notificationManager.notify(1, builder.build());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Ready to Protect", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
