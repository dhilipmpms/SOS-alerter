package org.sosalerter.app.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationListener;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
import android.net.Uri;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import org.sosalerter.app.MainActivity;
import org.sosalerter.app.R;
import org.sosalerter.app.SessionManager;
import org.sosalerter.app.data.db.entity.Contact;
import org.sosalerter.app.data.db.entity.EmergencySession;
import org.sosalerter.app.data.db.entity.LocationLog;
import org.sosalerter.app.data.db.entity.SmsLog;
import org.sosalerter.app.data.repository.EmergencyRepository;
import org.sosalerter.app.util.CameraCaptureHelper;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EmergencyService extends Service implements SensorEventListener {
    private static final String TAG = "EmergencyService";
    private static final String CHANNEL_ID = "emergency_service_channel";
    private static final int NOTIFICATION_ID = 444;

    private final IBinder binder = new LocalBinder();
    private ServiceListener listener;

    // Service state
    private boolean isMonitoring = false;
    private boolean isEmergencyMode = false;
    private boolean isCountdownCompleted = false;

    // Configurations
    private boolean isLoudMode = true;
    private boolean isSirenEnabled = true;
    private boolean isFlashlightEnabled = true;
    private int countdownTime = 10;
    private String triggerSource = "Manual Button";

    // Subsystems
    private SensorManager sensorManager;
    private Vibrator vibrator;
    private CameraManager cameraManager;
    private String backCameraId;
    private ToneGenerator toneGenerator;
    private MediaRecorder mediaRecorder;
    private LocationManager locationManager;
    private EmergencyRepository repository;
    private SessionManager sessionManager;

    // Trigger state tracking
    private final List<Long> screenPressTimes = new ArrayList<>();
    private final List<Long> volumePressTimes = new ArrayList<>();
    private long lastShakeTime = 0;
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private boolean isSpeechListening = false;

    // Execution handlers & runnables
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;
    private Runnable strobeRunnable;
    private Runnable sirenRunnable;
    private boolean strobeState = false;

    private final Runnable photoCaptureRunnable = new Runnable() {
        @Override
        public void run() {
            if (isEmergencyMode && isCountdownCompleted) {
                captureEvidencePhotos();
                handler.postDelayed(this, 10000);
            }
        }
    };

    // Current emergency records
    private int currentSessionId = -1;
    private String currentAudioPath = "";
    private String currentFrontPhotoPath = "";
    private String currentRearPhotoPath = "";
    private long emergencyStartTime = 0;
    private Location lastCapturedLocation = null;
    private long lastSmsSendTime = 0;

    public interface ServiceListener {
        void onCountdownTick(int secondsRemaining);
        void onEmergencyStateChanged(boolean isActive, boolean isCountdownCompleted);
        void onMonitoringStateChanged(boolean isMonitoring);
    }

    public class LocalBinder extends Binder {
        public EmergencyService getService() {
            return EmergencyService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        repository = new EmergencyRepository(this);
        sessionManager = new SessionManager(this);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        try {
            if (cameraManager.getCameraIdList().length > 0) {
                backCameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to get camera list", e);
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if ("ACTION_TRIGGER_SOS".equals(action)) {
                String source = intent.getStringExtra("EXTRA_TRIGGER_SOURCE");
                if (source != null) triggerSource = source;
                startEmergency();
            } else if ("ACTION_STOP_SOS".equals(action)) {
                stopEmergency();
            }
        }

        // Keep service running in appropriate mode
        syncServiceState();

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setListener(ServiceListener listener) {
        this.listener = listener;
        // Immediate update to new client
        if (listener != null) {
            listener.onMonitoringStateChanged(isMonitoring);
            listener.onEmergencyStateChanged(isEmergencyMode, isCountdownCompleted);
        }
    }

    public void startEmergency(String source) {
        if (source != null) {
            this.triggerSource = source;
        }
        startEmergency();
    }

    public void startEmergency() {
        if (isEmergencyMode) return;

        isEmergencyMode = true;
        isCountdownCompleted = false;
        countdownTime = 10;
        emergencyStartTime = System.currentTimeMillis();
        
        loadUserSettings();

        // Update notification
        safeStartForeground(NOTIFICATION_ID, buildEmergencyNotification("Emergency Countdown Started", "SOS triggered via " + triggerSource + ". Starting alerts in " + countdownTime + "s."));

        if (listener != null) {
            listener.onEmergencyStateChanged(true, false);
            listener.onCountdownTick(countdownTime);
        }

        // Start countdown loop
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (countdownTime > 0) {
                    if (listener != null) {
                        listener.onCountdownTick(countdownTime);
                    }
                    countdownTime--;
                    handler.postDelayed(this, 1000);
                } else {
                    isCountdownCompleted = true;
                    if (listener != null) {
                        listener.onEmergencyStateChanged(true, true);
                    }
                    executeEmergencyActions();
                }
            }
        };
        handler.post(countdownRunnable);

        // Turn on alerts (loud vs silent)
        if (isLoudMode) {
            startVibration();
            if (isSirenEnabled) startSiren();
            if (isFlashlightEnabled) startStrobeFlashlight();
        }
    }

    public void stopEmergency() {
        if (!isEmergencyMode) return;

        // Cleanup countdown
        if (countdownRunnable != null) {
            handler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }

        // Cleanup sirens / strobes
        stopSiren();
        stopStrobeFlashlight();
        stopVibration();
        stopAudioRecording();
        handler.removeCallbacks(photoCaptureRunnable);

        // Save session if completed or resolution
        if (isCountdownCompleted && currentSessionId != -1) {
            long endTime = System.currentTimeMillis();
            long duration = (endTime - emergencyStartTime) / 1000;
            
            final String finalAudioPath = currentAudioPath;
            final String finalFrontPhoto = currentFrontPhotoPath;
            final String finalRearPhoto = currentRearPhotoPath;
            
            repository.getSessionDetails(currentSessionId, (session, locations, smsLogs) -> {
                if (session != null) {
                    session.setEndTime(endTime);
                    session.setDuration(duration);
                    session.setAudioPath(finalAudioPath);
                    session.setFrontPhotoPath(finalFrontPhoto);
                    session.setRearPhotoPath(finalRearPhoto);
                    session.setResolved(true);
                    repository.updateSession(session);
                }
            });
            
            sendSafeSMS();
        }

        isEmergencyMode = false;
        isCountdownCompleted = false;
        currentSessionId = -1;
        currentAudioPath = "";
        currentFrontPhotoPath = "";
        currentRearPhotoPath = "";
        lastCapturedLocation = null;

        // Stop location updates
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }

        if (listener != null) {
            listener.onEmergencyStateChanged(false, false);
        }

        syncServiceState();
    }

    private int getAvailableForegroundServiceTypes() {
        int types = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                types |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                types |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                types |= android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            }
        }
        return types;
    }

    private void safeStartForeground(int id, Notification notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int types = getAvailableForegroundServiceTypes();
                if (types != 0) {
                    startForeground(id, notification, types);
                } else {
                    Log.w(TAG, "No specific permissions granted for location, camera, or mic. Starting foreground service with default settings.");
                    startForeground(id, notification);
                }
            } else {
                startForeground(id, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start foreground service safely", e);
        }
    }

    public void syncServiceState() {
        boolean shouldMonitor = sessionManager.isTriggerPowerTriple() ||
                sessionManager.isTriggerShake() ||
                sessionManager.isTriggerVoice() ||
                sessionManager.isTriggerVolumeCombo();

        if (isEmergencyMode) {
            // Already running high-priority foreground emergency
            return;
        }

        if (shouldMonitor) {
            if (!isMonitoring) {
                isMonitoring = true;
                safeStartForeground(NOTIFICATION_ID, buildMonitorNotification());
                registerBackgroundTriggers();
            }
        } else {
            if (isMonitoring) {
                isMonitoring = false;
                unregisterBackgroundTriggers();
                stopForeground(true);
                stopSelf();
            }
        }

        if (listener != null) {
            listener.onMonitoringStateChanged(isMonitoring);
        }
    }

    private void loadUserSettings() {
        isLoudMode = sessionManager.isLoudMode();
        isSirenEnabled = sessionManager.isSirenEnabled();
        isFlashlightEnabled = sessionManager.isFlashlightEnabled();
    }

    // BACKGROUND TRIGGER MANAGERS

    private void registerBackgroundTriggers() {
        // 1. Power Button Receiver (dynamic screen actions)
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, screenFilter);
        }

        // 2. Shake detection
        if (sessionManager.isTriggerShake()) {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accel != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_NORMAL);
            }
        }

        // 3. Voice activation
        if (sessionManager.isTriggerVoice()) {
            handler.post(this::startSpeechListening);
        }

        // 4. Volume combo listener (we can capture volume level changes dynamically)
        IntentFilter volFilter = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(volumeReceiver, volFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(volumeReceiver, volFilter);
        }
    }

    private void unregisterBackgroundTriggers() {
        try {
            unregisterReceiver(screenReceiver);
        } catch (Exception ignored) {}

        try {
            unregisterReceiver(volumeReceiver);
        } catch (Exception ignored) {}

        sensorManager.unregisterListener(this);
        stopSpeechListening();
    }

    private void triggerEmergencyFromBackground(String source) {
        if (!isEmergencyMode) {
            triggerSource = source;
            Intent intent = new Intent(this, EmergencyService.class);
            intent.setAction("ACTION_TRIGGER_SOS");
            intent.putExtra("EXTRA_TRIGGER_SOURCE", source);
            startService(intent);

            // Open the UI MainActivity to show countdown
            Intent mainIntent = new Intent(this, MainActivity.class);
            mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mainIntent);
        }
    }

    // POWER BUTTON MONITORING
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) || Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                long now = System.currentTimeMillis();
                screenPressTimes.add(now);

                // Filter out timestamps older than 3 seconds
                while (!screenPressTimes.isEmpty() && now - screenPressTimes.get(0) > 3000) {
                    screenPressTimes.remove(0);
                }

                if (screenPressTimes.size() >= 3) {
                    screenPressTimes.clear();
                    triggerEmergencyFromBackground("Power Button Triple Press");
                }
            }
        }
    };

    // VOLUME COMBO MONITORING
    private final BroadcastReceiver volumeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("android.media.VOLUME_CHANGED_ACTION".equals(intent.getAction())) {
                long now = System.currentTimeMillis();
                volumePressTimes.add(now);

                // Filter out timestamps older than 2.5 seconds
                while (!volumePressTimes.isEmpty() && now - volumePressTimes.get(0) > 2500) {
                    volumePressTimes.remove(0);
                }

                // If user clicks volume button multiple times rapidly in 2 seconds, trigger SOS
                if (volumePressTimes.size() >= 4 && sessionManager.isTriggerVolumeCombo()) {
                    volumePressTimes.clear();
                    triggerEmergencyFromBackground("Volume Buttons");
                }
            }
        }
    };

    // SHAKE MONITORING
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && sessionManager.isTriggerShake()) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            long now = System.currentTimeMillis();
            if ((now - lastShakeTime) > 300) {
                double gForce = Math.sqrt(x*x + y*y + z*z) / SensorManager.GRAVITY_EARTH;
                if (gForce > 2.8) { // Shake sensitivity threshold
                    lastShakeTime = now;
                    triggerEmergencyFromBackground("Device Shake");
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // VOICE MONITORING
    private void startSpeechListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. Voice trigger disabled.");
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                isSpeechListening = true;
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                isSpeechListening = false;
                // Restart listening after brief pause
                handler.postDelayed(() -> {
                    if (isMonitoring && !isEmergencyMode && sessionManager.isTriggerVoice()) {
                        startSpeechListening();
                    }
                }, 1000);
            }

            @Override
            public void onResults(Bundle results) {
                isSpeechListening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        if (match.toLowerCase().contains("help me")) {
                            triggerEmergencyFromBackground("Voice Activation");
                            return;
                        }
                    }
                }
                // Restart listening
                if (isMonitoring && !isEmergencyMode && sessionManager.isTriggerVoice()) {
                    startSpeechListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String match : matches) {
                        if (match.toLowerCase().contains("help me")) {
                            triggerEmergencyFromBackground("Voice Activation");
                            return;
                        }
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(speechIntent);
    }

    private void stopSpeechListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        isSpeechListening = false;
    }

    // SIREN SOUND GENERATOR
    private void startSiren() {
        try {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            sirenRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isEmergencyMode && isSirenEnabled && !isSilentMode() && toneGenerator != null) {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 450);
                        handler.postDelayed(this, 600);
                    }
                }
            };
            handler.post(sirenRunnable);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize siren", e);
        }
    }

    private void stopSiren() {
        if (sirenRunnable != null) {
            handler.removeCallbacks(sirenRunnable);
            sirenRunnable = null;
        }
        if (toneGenerator != null) {
            try {
                toneGenerator.stopTone();
                toneGenerator.release();
            } catch (Exception ignored) {}
            toneGenerator = null;
        }
    }

    // FLASHLIGHT STROBE
    private void startStrobeFlashlight() {
        if (backCameraId == null) return;
        
        strobeRunnable = new Runnable() {
            @Override
            public void run() {
                // If camera photo capture is currently running, temporarily pause strobe to prevent hardware access lock
                if (isEmergencyMode && isFlashlightEnabled && !isSilentMode()) {
                    strobeState = !strobeState;
                    toggleFlashlight(strobeState);
                    handler.postDelayed(this, 200);
                } else {
                    toggleFlashlight(false);
                }
            }
        };
        handler.post(strobeRunnable);
    }

    private void stopStrobeFlashlight() {
        if (strobeRunnable != null) {
            handler.removeCallbacks(strobeRunnable);
            strobeRunnable = null;
        }
        toggleFlashlight(false);
    }

    private void toggleFlashlight(boolean on) {
        if (backCameraId != null) {
            try {
                cameraManager.setTorchMode(backCameraId, on);
            } catch (Exception ignored) {}
        }
    }

    // VIBRATOR
    private void startVibration() {
        if (vibrator != null) {
            long[] pattern = {0, 500, 250, 500};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            } else {
                vibrator.vibrate(pattern, 0);
            }
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
        }
    }

    private boolean isSilentMode() {
        return !isLoudMode;
    }

    // EMERGENCY MODE ACTIONS (COOLDOWN COMPLETED)

    private void executeEmergencyActions() {
        // Change notification state
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildEmergencyNotification("Emergency Dispatching", "Alert dispatches and tracking initiated."));

        // 1. Save Emergency Session
        EmergencySession session = new EmergencySession(emergencyStartTime, triggerSource);
        repository.insertSession(session, sessionId -> {
            currentSessionId = sessionId;

            // Send immediate emergency SMS (within 1 second of countdown completion)
            handler.post(this::sendImmediateEmergencySMS);

            // 2. Start GPS Tracking
            handler.post(this::startLocationTracking);

            // 3. Record Audio
            handler.post(this::startAudioRecording);

            // 4. Capture Photos
            handler.post(photoCaptureRunnable);

            // 5. Trigger location-based initial SMS dispatch
            handler.post(this::startEmergencyAfterLocationAvailable);
        });
    }

    private void sendImmediateEmergencySMS() {
        String userName = sessionManager.getUserName();
        if (userName == null || userName.trim().isEmpty()) {
            userName = "The user";
        }
        String customMsg = sessionManager.getCustomMessage();
        String alertBody = (customMsg != null && !customMsg.trim().isEmpty())
                ? customMsg.trim()
                : (userName + " may be in danger.");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String message = "EMERGENCY ALERT\n\n" +
                alertBody + "\n\n" +
                "Emergency mode has been activated.\n" +
                "Location is currently being acquired.\n" +
                "Further updates will follow shortly.\n\n" +
                "Time: " + timestamp;

        dispatchSMSToContacts(message);
        lastSmsSendTime = System.currentTimeMillis();
    }

    // LOCATION TRACKING
    @SuppressLint("MissingPermission")
    private void startLocationTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permissions not granted. Cannot start location tracking.");
            return;
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        30000,
                        0,
                        locationListener,
                        Looper.getMainLooper()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to request location updates from GPS provider", e);
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        30000,
                        0,
                        locationListener,
                        Looper.getMainLooper()
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to request location updates from Network provider", e);
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            if (location != null && currentSessionId != -1) {
                lastCapturedLocation = location;
                saveLocationLog(location);
                
                // Check if we need to send periodic update SMS
                long now = System.currentTimeMillis();
                long intervalMs = sessionManager.getSmsIntervalSeconds() * 1000L;
                if (now - lastSmsSendTime >= intervalMs) {
                    sendPeriodicSMS(location);
                }
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}

        @Override
        public void onProviderEnabled(@NonNull String provider) {}

        @Override
        public void onProviderDisabled(@NonNull String provider) {}
    };

    @SuppressLint("MissingPermission")
    private Location getBestLastKnownLocation() {
        Location bestLocation = null;
        try {
            Location gpsLocation = null;
            Location netLocation = null;
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                netLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            
            if (gpsLocation != null && netLocation != null) {
                if (gpsLocation.getTime() > netLocation.getTime()) {
                    bestLocation = gpsLocation;
                } else {
                    bestLocation = netLocation;
                }
            } else if (gpsLocation != null) {
                bestLocation = gpsLocation;
            } else if (netLocation != null) {
                bestLocation = netLocation;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get last known location", e);
        }
        return bestLocation;
    }

    private void saveLocationLog(Location location) {
        String address = getAddressFromLatLng(location.getLatitude(), location.getLongitude());
        int batteryLevel = getBatteryLevel();
        LocationLog log = new LocationLog(currentSessionId, location.getLatitude(), location.getLongitude(), location.getAccuracy(), address, batteryLevel, System.currentTimeMillis());
        repository.insertLocationLog(log);
    }

    private String getAddressFromLatLng(double lat, double lng) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    sb.append(addr.getAddressLine(i));
                    if (i < addr.getMaxAddressLineIndex()) sb.append(", ");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            Log.e(TAG, "Geocoder failed", e);
        }
        return "Unknown address";
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    // AUDIO CAPTURE
    private void startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission not granted. Skipping audio recording.");
            return;
        }
        try {
            File dir = new File(getFilesDir(), "evidence");
            if (!dir.exists()) dir.mkdirs();
            File audioFile = new File(dir, "audio_" + System.currentTimeMillis() + ".m4a");
            currentAudioPath = audioFile.getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(currentAudioPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            Log.e(TAG, "Failed to start audio recording", e);
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.release();
                } catch (Exception ignored) {}
                mediaRecorder = null;
            }
            currentAudioPath = "";
        }
    }

    private void stopAudioRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Failed to stop media recorder", e);
            }
            mediaRecorder = null;
        }
    }

    // PHOTO CAPTURE
    private void captureEvidencePhotos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAMERA permission not granted. Skipping photo capture.");
            return;
        }
        // Temporarily pause strobe flashlight during photo capture to avoid camera resource collision
        stopStrobeFlashlight();
        
        CameraCaptureHelper cameraHelper = new CameraCaptureHelper(this);
        cameraHelper.capturePhotos("session_" + currentSessionId, new CameraCaptureHelper.CameraCaptureCallback() {
            @Override
            public void onCaptured(String frontPhotoPath, String rearPhotoPath) {
                currentFrontPhotoPath = frontPhotoPath;
                currentRearPhotoPath = rearPhotoPath;
                
                // Save paths back to active session
                if (currentSessionId != -1) {
                    repository.getSessionDetails(currentSessionId, (session, locations, smsLogs) -> {
                        if (session != null) {
                            session.setFrontPhotoPath(frontPhotoPath);
                            session.setRearPhotoPath(rearPhotoPath);
                            repository.updateSession(session);
                        }
                    });
                }

                // Resume strobe if emergency mode is active
                handler.post(() -> {
                    if (isEmergencyMode && isFlashlightEnabled && !isSilentMode()) {
                        startStrobeFlashlight();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Camera snapshot error: " + message);
                handler.post(() -> {
                    if (isEmergencyMode && isFlashlightEnabled && !isSilentMode()) {
                        startStrobeFlashlight();
                    }
                });
            }
        });
    }

    // ALERTS DISPATCH (SMS)

    // ALERTS DISPATCH (SMS)

    private void sendInitialSMS(Location location) {
        String userName = sessionManager.getUserName();
        if (userName == null || userName.trim().isEmpty()) {
            userName = "The user";
        }
        String customMsg = sessionManager.getCustomMessage();
        String alertBody = (customMsg != null && !customMsg.trim().isEmpty())
                ? customMsg.trim()
                : (userName + " may be in danger.");
        String latStr = String.valueOf(location.getLatitude());
        String lngStr = String.valueOf(location.getLongitude());
        String mapsUrl = "https://maps.google.com/?q=" + latStr + "," + lngStr;
        String accuracy = String.valueOf(location.getAccuracy()) + " meters";
        String address = getAddressFromLatLng(location.getLatitude(), location.getLongitude());
        if (address == null || address.trim().isEmpty() || address.equals("Unknown address")) {
            address = "Not Available";
        }
        
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(location.getTime()));

        String message = "EMERGENCY ALERT\n\n" +
                alertBody + "\n\n" +
                "Latitude: " + latStr + "\n\n" +
                "Longitude: " + lngStr + "\n\n" +
                "Address:\n\n" + address + "\n\n" +
                "Accuracy: " + accuracy + "\n\n" +
                "Google Maps:\n" + mapsUrl + "\n\n" +
                "Time: " + timestamp;

        dispatchSMSToContacts(message);
        lastSmsSendTime = System.currentTimeMillis();
    }

    private void sendFallbackSMS() {
        String userName = sessionManager.getUserName();
        if (userName == null || userName.trim().isEmpty()) {
            userName = "The user";
        }
        String customMsg = sessionManager.getCustomMessage();
        String alertBody = (customMsg != null && !customMsg.trim().isEmpty())
                ? customMsg.trim()
                : (userName + " may be in danger.");
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String message = "EMERGENCY ALERT\n\n" +
                alertBody + "\n\n" +
                "Latitude: Not Available\n\n" +
                "Longitude: Not Available\n\n" +
                "Address:\n\nNot Available\n\n" +
                "Accuracy: Not Available\n\n" +
                "Google Maps:\nLocation Unavailable\n\n" +
                "Time: " + timestamp;
        dispatchSMSToContacts(message);
        lastSmsSendTime = System.currentTimeMillis();
    }

    private void sendPeriodicSMS(Location location) {
        String latStr = String.valueOf(location.getLatitude());
        String lngStr = String.valueOf(location.getLongitude());
        String mapsUrl = "https://maps.google.com/?q=" + latStr + "," + lngStr;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(location.getTime()));

        String message = "SOS LOCATION UPDATE\n\n" +
                "Current Location: " + mapsUrl + "\n\n" +
                "Time: " + timestamp;

        dispatchSMSToContacts(message);
        lastSmsSendTime = System.currentTimeMillis();
    }

    private void sendSafeSMS() {
        String message = "EMERGENCY RESOLVED\n\n" +
                "The user has marked themselves safe.\n\n" +
                "Emergency monitoring has ended.";
        dispatchSMSToContacts(message);
    }

    private void dispatchSMSToContacts(String message) {
        repository.executor.execute(() -> {
            List<Contact> contacts = repository.getAllContactsSync();
            if (contacts == null || contacts.isEmpty()) {
                Log.w(TAG, "No emergency contacts configured to dispatch SMS.");
                return;
            }

            for (Contact c : contacts) {
                final String phone = c.getPhoneNumber();
                handler.post(() -> sendSmsWithRetry(phone, message, 1));
            }
        });
    }

    private void sendSmsWithRetry(String phoneNumber, String message, int attempt) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "SEND_SMS permission not granted. Cannot send SMS.");
            saveSmsLog(phoneNumber, message, "FAILED: Permission Denied");
            return;
        }
        SmsManager smsManager;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            smsManager = getSystemService(SmsManager.class);
        } else {
            smsManager = SmsManager.getDefault();
        }

        String SENT = "SMS_SENT_" + phoneNumber + "_" + System.currentTimeMillis() + "_" + attempt;
        PendingIntent sentPI = PendingIntent.getBroadcast(
                this, 0, new Intent(SENT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        BroadcastReceiver sentReceiver = new BroadcastReceiver() {
            private boolean processed = false;
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                if (processed) return;
                processed = true;
                try {
                    unregisterReceiver(this);
                } catch (Exception ignored) {}
                
                int resultCode = getResultCode();
                if (resultCode == android.app.Activity.RESULT_OK) {
                    Log.d(TAG, "SMS sent successfully to " + phoneNumber);
                    saveSmsLog(phoneNumber, message, "SENT");
                    Toast.makeText(EmergencyService.this, "Emergency Alerts Sent Successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Log.e(TAG, "SMS send failed to " + phoneNumber + ", error code: " + resultCode);
                    saveSmsLog(phoneNumber, message, "FAILED: error code " + resultCode);
                    if (attempt < 3) {
                        Log.d(TAG, "Retrying SMS to " + phoneNumber + ", attempt " + (attempt + 1));
                        handler.postDelayed(() -> sendSmsWithRetry(phoneNumber, message, attempt + 1), 5000);
                    } else {
                        showSmsFailureNotification(phoneNumber);
                    }
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sentReceiver, new IntentFilter(SENT), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(sentReceiver, new IntentFilter(SENT));
        }

        try {
            ArrayList<String> parts = smsManager.divideMessage(message);
            ArrayList<PendingIntent> sentIntents = new ArrayList<>();
            for (int i = 0; i < parts.size(); i++) {
                if (i == parts.size() - 1) {
                    sentIntents.add(sentPI);
                } else {
                    sentIntents.add(null);
                }
            }
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentIntents, null);
        } catch (Exception e) {
            Log.e(TAG, "Exception sending SMS to " + phoneNumber, e);
            try {
                unregisterReceiver(sentReceiver);
            } catch (Exception ignored) {}
            saveSmsLog(phoneNumber, message, "FAILED: exception " + e.getMessage());
            if (attempt < 3) {
                handler.postDelayed(() -> sendSmsWithRetry(phoneNumber, message, attempt + 1), 5000);
            } else {
                showSmsFailureNotification(phoneNumber);
            }
        }
    }

    private void saveSmsLog(String contactPhone, String message, String status) {
        if (currentSessionId != -1) {
            SmsLog log = new SmsLog(currentSessionId, contactPhone, message, System.currentTimeMillis(), status);
            repository.insertSmsLog(log);
        }
    }

    private void showSmsFailureNotification(String phoneNumber) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS Alert Failed")
                .setContentText("Could not send emergency SMS to " + phoneNumber + " after 3 attempts.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        manager.notify((int) System.currentTimeMillis(), notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOS Alerter Service",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Background monitoring and active emergency alerts channel.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildMonitorNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("SOS Alerter Enabled")
                .setContentText("Listening for active safety triggers...")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private Notification buildEmergencyNotification(String title, String content) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Intent cancelIntent = new Intent(this, EmergencyService.class);
        cancelIntent.setAction("ACTION_STOP_SOS");
        PendingIntent pendingCancel = PendingIntent.getService(
                this, 1, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_CALL)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "CANCEL SOS", pendingCancel)
                .build();
    }

    @SuppressLint("MissingPermission")
    private void startEmergencyAfterLocationAvailable() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Location permissions not granted. Falling back to default message dispatch.");
            sendFallbackSMS();
            triggerCallIfEnabled();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                String provider = null;
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    provider = LocationManager.GPS_PROVIDER;
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    provider = LocationManager.NETWORK_PROVIDER;
                }

                if (provider != null) {
                    android.os.CancellationSignal cancellationSignal = new android.os.CancellationSignal();
                    locationManager.getCurrentLocation(
                            provider,
                            cancellationSignal,
                            ContextCompat.getMainExecutor(this),
                            location -> {
                                if (location != null) {
                                    lastCapturedLocation = location;
                                    sendInitialSMS(location);
                                } else {
                                    fallbackToLastKnownLocation();
                                }
                                triggerCallIfEnabled();
                            }
                    );
                    return;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get current location using getCurrentLocation API 30+", e);
            }
        }

        fallbackToLastKnownLocation();
        triggerCallIfEnabled();
    }

    @SuppressLint("MissingPermission")
    private void fallbackToLastKnownLocation() {
        Location bestLoc = getBestLastKnownLocation();
        if (bestLoc != null) {
            lastCapturedLocation = bestLoc;
            sendInitialSMS(bestLoc);
        } else {
            sendFallbackSMS();
        }
    }

    private void triggerCallIfEnabled() {
        if (sessionManager.isEmergencyCallingEnabled()) {
            String phone = sessionManager.getEmergencyCallPhone();
            if (phone != null && !phone.trim().isEmpty()) {
                int delaySeconds = sessionManager.getEmergencyCallDelay();
                handler.postDelayed(() -> {
                    if (isEmergencyMode) {
                        makeEmergencyCall(phone);
                    }
                }, delaySeconds * 1000L);
            }
        }
    }

    private void makeEmergencyCall(String phone) {
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            try {
                Intent intent = new Intent(Intent.ACTION_CALL);
                intent.setData(Uri.parse("tel:" + phone));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            } catch (Exception e) {
                Log.e(TAG, "Failed to place call automatically", e);
            }
        } else {
            Log.w(TAG, "CALL_PHONE permission not granted. Cannot place automatic call.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBackgroundTriggers();
        stopSiren();
        stopStrobeFlashlight();
        stopVibration();
        stopAudioRecording();
    }
}
