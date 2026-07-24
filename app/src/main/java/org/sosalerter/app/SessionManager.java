package org.sosalerter.app;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "SOSAlerterPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    
    private static final String KEY_USER_NAME = "userName";
    private static final String KEY_CUSTOM_MESSAGE = "customMessage";
    private static final String KEY_SMS_INTERVAL = "smsInterval";

    // Triggers
    private static final String KEY_TRIGGER_POWER_TRIPLE = "triggerPowerTriple";
    private static final String KEY_TRIGGER_LONG_PRESS = "triggerLongPress";
    private static final String KEY_TRIGGER_VOLUME_COMBO = "triggerVolumeCombo";
    private static final String KEY_TRIGGER_SHAKE = "triggerShake";
    private static final String KEY_TRIGGER_VOICE = "triggerVoice";

    // Alarm styles
    private static final String KEY_SIREN_ENABLED = "sirenEnabled";
    private static final String KEY_FLASHLIGHT_ENABLED = "flashlightEnabled";
    private static final String KEY_LOUD_MODE = "loudMode";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void setLoggedIn(boolean isLoggedIn) {
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    private static final String KEY_IS_SETUP_COMPLETED = "isSetupCompleted";

    public void setSetupCompleted(boolean completed) {
        editor.putBoolean(KEY_IS_SETUP_COMPLETED, completed);
        editor.apply();
    }

    public boolean isSetupCompleted() {
        return prefs.getBoolean(KEY_IS_SETUP_COMPLETED, false);
    }

    private static final String KEY_WELCOME_SHOWN = "welcomeShown";

    public void setWelcomeShown(boolean shown) {
        editor.putBoolean(KEY_WELCOME_SHOWN, shown);
        editor.apply();
    }

    public boolean isWelcomeShown() {
        return prefs.getBoolean(KEY_WELCOME_SHOWN, false);
    }

    private static final String KEY_EMERGENCY_CALLING_ENABLED = "emergencyCallingEnabled";
    private static final String KEY_EMERGENCY_CALL_PHONE = "emergencyCallPhone";
    private static final String KEY_EMERGENCY_CALL_DELAY = "emergencyCallDelay";

    public void setEmergencyCallingEnabled(boolean enabled) {
        editor.putBoolean(KEY_EMERGENCY_CALLING_ENABLED, enabled);
        editor.apply();
    }

    public boolean isEmergencyCallingEnabled() {
        return prefs.getBoolean(KEY_EMERGENCY_CALLING_ENABLED, false);
    }

    public void setEmergencyCallPhone(String phone) {
        editor.putString(KEY_EMERGENCY_CALL_PHONE, phone);
        editor.apply();
    }

    public String getEmergencyCallPhone() {
        return prefs.getString(KEY_EMERGENCY_CALL_PHONE, "");
    }

    public void setEmergencyCallDelay(int seconds) {
        editor.putInt(KEY_EMERGENCY_CALL_DELAY, seconds);
        editor.apply();
    }

    public int getEmergencyCallDelay() {
        return prefs.getInt(KEY_EMERGENCY_CALL_DELAY, 10);
    }

    public void saveUserName(String name) {
        editor.putString(KEY_USER_NAME, name);
        editor.apply();
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public void saveCustomMessage(String message) {
        editor.putString(KEY_CUSTOM_MESSAGE, message);
        editor.apply();
    }

    public String getCustomMessage() {
        return prefs.getString(KEY_CUSTOM_MESSAGE, "");
    }

    public void saveSmsIntervalSeconds(int seconds) {
        editor.putInt(KEY_SMS_INTERVAL, seconds);
        editor.apply();
    }

    public int getSmsIntervalSeconds() {
        return prefs.getInt(KEY_SMS_INTERVAL, 60); // default every 60 seconds
    }

    // TRIGGER SETTERS/GETTERS

    public void setTriggerPowerTriple(boolean enabled) {
        editor.putBoolean(KEY_TRIGGER_POWER_TRIPLE, enabled);
        editor.apply();
    }

    public boolean isTriggerPowerTriple() {
        return prefs.getBoolean(KEY_TRIGGER_POWER_TRIPLE, true); // default enabled
    }

    public void setTriggerLongPress(boolean enabled) {
        editor.putBoolean(KEY_TRIGGER_LONG_PRESS, enabled);
        editor.apply();
    }

    public boolean isTriggerLongPress() {
        return prefs.getBoolean(KEY_TRIGGER_LONG_PRESS, false);
    }

    public void setTriggerVolumeCombo(boolean enabled) {
        editor.putBoolean(KEY_TRIGGER_VOLUME_COMBO, enabled);
        editor.apply();
    }

    public boolean isTriggerVolumeCombo() {
        return prefs.getBoolean(KEY_TRIGGER_VOLUME_COMBO, false);
    }

    public void setTriggerShake(boolean enabled) {
        editor.putBoolean(KEY_TRIGGER_SHAKE, enabled);
        editor.apply();
    }

    public boolean isTriggerShake() {
        return prefs.getBoolean(KEY_TRIGGER_SHAKE, false);
    }

    public void setTriggerVoice(boolean enabled) {
        editor.putBoolean(KEY_TRIGGER_VOICE, enabled);
        editor.apply();
    }

    public boolean isTriggerVoice() {
        return prefs.getBoolean(KEY_TRIGGER_VOICE, false);
    }

    // ALARM PROFILE SETTERS/GETTERS

    public void setSirenEnabled(boolean enabled) {
        editor.putBoolean(KEY_SIREN_ENABLED, enabled);
        editor.apply();
    }

    public boolean isSirenEnabled() {
        return prefs.getBoolean(KEY_SIREN_ENABLED, true);
    }

    public void setFlashlightEnabled(boolean enabled) {
        editor.putBoolean(KEY_FLASHLIGHT_ENABLED, enabled);
        editor.apply();
    }

    public boolean isFlashlightEnabled() {
        return prefs.getBoolean(KEY_FLASHLIGHT_ENABLED, true);
    }

    public void setLoudMode(boolean enabled) {
        editor.putBoolean(KEY_LOUD_MODE, enabled);
        editor.apply();
    }

    public boolean isLoudMode() {
        return prefs.getBoolean(KEY_LOUD_MODE, true);
    }
}
