package com.example.saftyapp;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin = findViewById(R.id.tvLogin);

        btnRegister.setOnClickListener(v -> {
            // For now, redirect to dashboard after registration
            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
            // Finish all previous activities to prevent going back to register/login
            finishAffinity();
        });

        tvLogin.setOnClickListener(v -> {
            finish(); // Go back to login
        });
    }
}
