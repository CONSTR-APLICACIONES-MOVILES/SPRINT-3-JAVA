package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button continueButton = findViewById(R.id.continue_button);
        View googleButton = findViewById(R.id.google_button);
        findViewById(R.id.restored_session_card).setVisibility(View.GONE);
        continueButton.setEnabled(false);
        googleButton.setEnabled(false);
        findViewById(R.id.sign_in_button).setEnabled(false);
        findViewById(R.id.forgot_password).setEnabled(false);
        findViewById(R.id.create_account).setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
