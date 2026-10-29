package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.IconButtons;

import java.util.ArrayList;
import java.util.List;

/** Create-group view shell. Group creation and invitation behavior are restored on their feature branch. */
public class CreateGroupActivity extends AppCompatActivity {
// f6: the backend needs a category and this form does not have one yet, so we use social
    private static final String DEFAULT_CATEGORY = "social";
// f6: the backend accepts names between 2 and 80 characters
    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 80;
// key to save the emails when the phone turns
    private static final String STATE_INVITES = "invites";

// f6: emails the user added, they become the pending invites of the group
    private final List<String> invites = new ArrayList<>();
    private EditText nameInput;
    private EditText emailInput;
    private LinearLayout emailChips;
    private View createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        findViewById(R.id.back_group_button).setOnClickListener(v -> finish());
        findViewById(R.id.cancel_create_group).setOnClickListener(v -> finish());
        findViewById(R.id.copy_group_link_button).setEnabled(false);
        findViewById(R.id.share_group_link_button).setEnabled(false);
        findViewById(R.id.show_qr_button).setEnabled(false);
        findViewById(R.id.header_notif_btn).setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        findViewById(R.id.header_profile_btn).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
// shared bottom bar, groups is the active tab
        BottomNav.setup(this, BottomNav.Tab.GROUPS);

// f6: the form to create the group and add the emails
        nameInput = findViewById(R.id.group_name_input);
        emailInput = findViewById(R.id.email_input_group);
        emailChips = findViewById(R.id.email_chips);
        emailChips.setOrientation(LinearLayout.VERTICAL);
        createButton = findViewById(R.id.create_group_button);
// the icon goes next to the text, both in the middle of the button
        IconButtons.centerIconWithText((TextView) createButton);
        findViewById(R.id.add_email_button).setOnClickListener(v -> addEmail());
        createButton.setOnClickListener(v -> createGroup());
// when the phone turns android makes the screen again, so we bring back the emails we saved
        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(STATE_INVITES);
            if (saved != null) {
                invites.addAll(saved);
            }
        }
        renderInvites();
    }

// we save the emails before android makes the screen again, the name field saves itself
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_INVITES, new ArrayList<>(invites));
    }

// f6: checks the email and adds it to the list if it is new
    private void addEmail() {
        String email = emailInput.getText().toString().trim().toLowerCase();
        if (email.isEmpty()) {
            emailInput.setError("Enter an email");
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            return;
        }
        if (invites.contains(email)) {
            emailInput.setError("This email is already in the list");
            return;
        }
        invites.add(email);
        emailInput.setText("");
        renderInvites();
    }

// f6: draws one row for each email, a tap on the row removes it
    private void renderInvites() {
        emailChips.removeAllViews();
        emailChips.setVisibility(invites.isEmpty() ? View.GONE : View.VISIBLE);
        for (String email : invites) {
            TextView row = new TextView(this);
            row.setText(email + "   •   Pending   ×");
            row.setTextSize(11);
            row.setTextColor(getColor(R.color.brand_primary));
            row.setBackgroundResource(R.drawable.bg_light_blue);
            int padding = Math.round(10 * getResources().getDisplayMetrics().density);
            row.setPadding(padding, padding, padding, padding);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = padding / 2;
            row.setLayoutParams(params);
            row.setOnClickListener(v -> {
                invites.remove(email);
                renderInvites();
            });
            emailChips.addView(row);
        }
    }

// f6: checks the name and asks the repository to create the group with the invites
    private void createGroup() {
        String name = nameInput.getText().toString().trim();
        if (name.length() < MIN_NAME_LENGTH || name.length() > MAX_NAME_LENGTH) {
            nameInput.setError("Use a name between " + MIN_NAME_LENGTH + " and " + MAX_NAME_LENGTH + " characters");
            return;
        }
// f6: we turn off the button so the user can not create the same group two times
        createButton.setEnabled(false);
        Repositories.groups().createGroup(name, DEFAULT_CATEGORY, new ArrayList<>(invites), new Callback<String>() {
            @Override
            public void onSuccess(String groupId) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                toast("Group created");
                startActivity(new Intent(CreateGroupActivity.this, GroupDetailActivity.class)
                        .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, groupId));
                finish();
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                createButton.setEnabled(true);
                toast("Could not create the group. Try again.");
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
