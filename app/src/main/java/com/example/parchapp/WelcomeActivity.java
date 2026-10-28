package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;

/* It's theb first screen of the app. Returning users with a saved session skip it and land on Sign In, which offers "Continue as". */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Repositories.auth().getSignedInUser() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_welcome);

        highlightLastWord(findViewById(R.id.welcome_headline));
        findViewById(R.id.get_started_button).setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
        findViewById(R.id.have_account_button).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
    }

    /* Paints "share." in the brand green, as in the design. */
    private void highlightLastWord(TextView headline) {
        String text = headline.getText().toString();
        int start = text.lastIndexOf(' ') + 1;
        SpannableString styled = new SpannableString(text);
        styled.setSpan(new ForegroundColorSpan(getColor(R.color.brand_success)), start, text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        headline.setText(styled);
    }
}
