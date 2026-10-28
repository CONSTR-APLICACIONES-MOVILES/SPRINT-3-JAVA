package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;


public class CreateAccountActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        EditText passwordInput = findViewById(R.id.create_password_input);
        EditText confirmInput = findViewById(R.id.create_confirm_password_input);

        findViewById(R.id.create_back_button).setOnClickListener(v -> finish());
        // Reached from Welcome or from Sign In; either way this link must land on Sign In.
        findViewById(R.id.back_to_sign_in).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        findViewById(R.id.create_google_button).setEnabled(false);
        findViewById(R.id.google_account_card).setVisibility(View.GONE);

        setupVisibilityToggle(findViewById(R.id.toggle_password), passwordInput);
        setupVisibilityToggle(findViewById(R.id.toggle_confirm_password), confirmInput);
        findViewById(R.id.create_account_button).setEnabled(false);
    }

    private void setupVisibilityToggle(ImageButton toggle, EditText field) {
        toggle.setOnClickListener(v -> {
            boolean hidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            field.setTransformationMethod(hidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            toggle.setImageResource(hidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            field.setSelection(field.getText().length());
        });
    }

}
