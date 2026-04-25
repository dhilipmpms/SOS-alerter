package com.example.saftyapp;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "SaftyAppPrefs";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_PHONE_NUMBER = "phoneNumber";
    private static final String KEY_CUSTOM_MESSAGE = "customMessage";

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

    public void saveEmergencyDetails(String phone, String message) {
        editor.putString(KEY_PHONE_NUMBER, phone);
        editor.putString(KEY_CUSTOM_MESSAGE, message);
        editor.apply();
    }

    public String getPhoneNumber() {
        return prefs.getString(KEY_PHONE_NUMBER, "");
    }

    public String getCustomMessage() {
        return prefs.getString(KEY_CUSTOM_MESSAGE, "");
    }
}
