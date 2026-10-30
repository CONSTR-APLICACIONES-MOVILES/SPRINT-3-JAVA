package com.example.parchapp;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.AnalyticsEvents;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.GroupDraftStore;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.GroupForm;
import com.example.parchapp.domain.model.GroupDraft;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.IconButtons;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// f6: screen to create a group with a name, a date, a time and the emails of the people to invite
public class CreateGroupActivity extends AppCompatActivity {
// f6: the backend needs a category and this form does not have one yet, so we use social
    private static final String DEFAULT_CATEGORY = "social";
// keys to save the emails, the date and the time when the phone turns
    private static final String STATE_INVITES = "invites";
    private static final String STATE_DATE = "date";
    private static final String STATE_TIME = "time";
    private static final int NONE = -1;

// f6: emails the user added, they become the pending invites of the group
    private final List<String> invites = new ArrayList<>();
    private EditText nameInput;
    private EditText emailInput;
    private EditText dateInput;
    private EditText timeInput;
    private TextView nameCounter;
    private LinearLayout emailChips;
    private View createButton;
// the day and the minute of the day the user picked, none means not picked yet
    private long pickedDate = NONE;
    private int pickedMinute = NONE;
// strategy 7: the draft of this form is saved on the phone while the user writes
    private GroupDraftStore draftStore;
// true while we put the saved draft back, so that does not save the draft again
    private boolean restoringDraft;
// without connection the create button turns off, while the group is being created it is off too
    private boolean online = true;
    private boolean creating;
    private final OfflineBannerController offlineBanner = new OfflineBannerController();
    private final AnalyticsTracker.ConnectivityListener connectivity = isOnline -> runOnUiThread(() -> {
        online = isOnline;
        renderConnectivity();
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        findViewById(R.id.back_group_button).setOnClickListener(v -> finish());
        findViewById(R.id.cancel_create_group).setOnClickListener(v -> finish());
        findViewById(R.id.copy_group_link_button).setEnabled(false);
        findViewById(R.id.share_group_link_button).setEnabled(false);
        findViewById(R.id.show_qr_button).setEnabled(false);
// shared top bar with the logo, search, alerts and the user initials
        TopHeader.setup(this);
// shared bottom bar, groups is the active tab
        BottomNav.setup(this, BottomNav.Tab.GROUPS);

// f6: the form to create the group and add the emails
        nameInput = findViewById(R.id.group_name_input);
        nameCounter = findViewById(R.id.group_name_counter);
        emailInput = findViewById(R.id.email_input_group);
        dateInput = findViewById(R.id.activity_date_input);
        timeInput = findViewById(R.id.activity_time_input);
        emailChips = findViewById(R.id.email_chips);
        emailChips.setOrientation(LinearLayout.VERTICAL);
        createButton = findViewById(R.id.create_group_button);
// the icon goes next to the text, both in the middle of the button
        IconButtons.centerIconWithText((TextView) createButton);
        findViewById(R.id.add_email_button).setOnClickListener(v -> addEmail());
        createButton.setOnClickListener(v -> createGroup());
        setupInputRules();
// when the phone turns android makes the screen again, so we bring back the emails we saved
        if (savedInstanceState != null) {
            ArrayList<String> saved = savedInstanceState.getStringArrayList(STATE_INVITES);
            if (saved != null) {
                invites.addAll(saved);
            }
            pickedDate = savedInstanceState.getLong(STATE_DATE, NONE);
            pickedMinute = savedInstanceState.getInt(STATE_TIME, NONE);
        }
        UserProfile user = UserSession.getCurrentUser();
        draftStore = new GroupDraftStore(this, user == null ? null : user.getId());
// when the screen opens for the first time we bring back the draft the user left before
        if (savedInstanceState == null) {
            restoreDraft();
        }
        renderInvites();
        showNameCount();
    }

// while the screen is visible we listen to the connection, so the button and the notice follow it
    @Override
    protected void onStart() {
        super.onStart();
        offlineBanner.start(findViewById(R.id.offline_banner));
        AnalyticsTracker.getInstance().addConnectivityListener(connectivity);
    }

    @Override
    protected void onStop() {
        AnalyticsTracker.getInstance().removeConnectivityListener(connectivity);
        offlineBanner.stop();
        super.onStop();
    }

// without connection the button is off and the notice says the draft is saved, with connection it comes back alone
    private void renderConnectivity() {
        boolean canCreate = online && !creating;
        createButton.setEnabled(canCreate);
        createButton.setAlpha(online ? 1f : 0.5f);
        findViewById(R.id.group_offline_notice).setVisibility(online ? View.GONE : View.VISIBLE);
    }

// puts the saved draft in the form, the texts change but that does not save the draft again
    private void restoreDraft() {
        GroupDraft draft = draftStore.load();
        if (draft == null) {
            return;
        }
        restoringDraft = true;
        nameInput.setText(draft.getName());
        pickedDate = draft.getPickedDate();
        pickedMinute = draft.getPickedMinute();
        dateInput.setText(draft.getDateText());
        timeInput.setText(draft.getTimeText());
        invites.clear();
        invites.addAll(draft.getEmails());
        restoringDraft = false;
        toast(getString(R.string.group_draft_restored));
    }

// saves what is in the form now, it runs after every change
    private void saveDraft() {
        if (restoringDraft || draftStore == null) {
            return;
        }
        draftStore.save(new GroupDraft(nameInput.getText().toString(), pickedDate, pickedMinute,
                dateInput.getText().toString(), timeInput.getText().toString(), invites));
    }

// we save the emails, the date and the time before android makes the screen again, the fields save their text
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_INVITES, new ArrayList<>(invites));
        outState.putLong(STATE_DATE, pickedDate);
        outState.putInt(STATE_TIME, pickedMinute);
    }

// limits of each field, the user can not type more than the backend accepts
    private void setupInputRules() {
        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                showNameCount();
                saveDraft();
            }
        });
// emails have no spaces, so spaces are not typed
        emailInput.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(GroupForm.MAX_EMAIL_LENGTH),
                (source, start, end, dest, dstart, dend) -> {
                    String typed = source.subSequence(start, end).toString();
                    return typed.matches("(?s).*\\s.*") ? typed.replaceAll("\\s+", "") : null;
                }});
// the done key of the keyboard adds the email like the add button
        emailInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addEmail();
                return true;
            }
            return false;
        });
// date and time are picked from the android calendar and clock, they can not be typed
        dateInput.setOnClickListener(v -> pickDate());
        timeInput.setOnClickListener(v -> pickTime());
    }

// shows how many characters the name has and the most it can have
    private void showNameCount() {
        nameCounter.setText(getString(R.string.group_name_counter,
                nameInput.length(), GroupForm.MAX_NAME_LENGTH));
    }

// the calendar starts today and days in the past can not be picked
    private void pickDate() {
        Calendar day = Calendar.getInstance();
        if (pickedDate != NONE) {
            day.setTimeInMillis(pickedDate);
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.clear();
            picked.set(year, month, dayOfMonth);
            pickedDate = picked.getTimeInMillis();
            dateInput.setText(DateFormat.getMediumDateFormat(this).format(picked.getTime()));
            dateInput.setError(null);
            isTimeInTheFuture();
            saveDraft();
        }, day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

// the clock starts in the time the user picked before or in the current time
    private void pickTime() {
        Calendar now = Calendar.getInstance();
        int hour = pickedMinute == NONE ? now.get(Calendar.HOUR_OF_DAY) : pickedMinute / 60;
        int minute = pickedMinute == NONE ? now.get(Calendar.MINUTE) : pickedMinute % 60;
        new TimePickerDialog(this, (picker, hourOfDay, minuteOfHour) -> {
            pickedMinute = hourOfDay * 60 + minuteOfHour;
            Calendar picked = Calendar.getInstance();
            picked.set(Calendar.HOUR_OF_DAY, hourOfDay);
            picked.set(Calendar.MINUTE, minuteOfHour);
            timeInput.setText(DateFormat.getTimeFormat(this).format(picked.getTime()));
            isTimeInTheFuture();
            saveDraft();
        }, hour, minute, DateFormat.is24HourFormat(this)).show();
    }

// if the plan is today the time can not be in the past
    private boolean isTimeInTheFuture() {
        if (pickedDate == NONE || pickedMinute == NONE) {
            timeInput.setError(null);
            return true;
        }
        Calendar plan = Calendar.getInstance();
        plan.setTimeInMillis(pickedDate);
        plan.set(Calendar.HOUR_OF_DAY, pickedMinute / 60);
        plan.set(Calendar.MINUTE, pickedMinute % 60);
        boolean future = plan.getTimeInMillis() > System.currentTimeMillis();
        timeInput.setError(future ? null : getString(R.string.group_time_past));
        return future;
    }

// f6: checks the email and adds it to the list if it is new
    private void addEmail() {
        String email = GroupForm.cleanEmail(emailInput.getText().toString());
        if (email.isEmpty()) {
            emailInput.setError(getString(R.string.group_email_empty));
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.group_email_invalid));
            return;
        }
        if (invites.contains(email)) {
            emailInput.setError(getString(R.string.group_email_repeated));
            return;
        }
// the user is the owner of the group, so they do not need an invite
        UserProfile user = UserSession.getCurrentUser();
        if (user != null && email.equalsIgnoreCase(user.getEmail())) {
            emailInput.setError(getString(R.string.group_email_yourself));
            return;
        }
        if (invites.size() >= GroupForm.MAX_INVITES) {
            emailInput.setError(getString(R.string.group_invites_limit, GroupForm.MAX_INVITES));
            return;
        }
        invites.add(email);
        // bq8: adding an invite is a use of the invitations feature
        AnalyticsTracker.getInstance().trackFeature(AnalyticsEvents.FEATURE_INVITATIONS, "create_group");
        emailInput.setText("");
        renderInvites();
        saveDraft();
    }

// f6: draws one row for each email, a tap on the row removes it
    private void renderInvites() {
        emailChips.removeAllViews();
        emailChips.setVisibility(invites.isEmpty() ? View.GONE : View.VISIBLE);
        for (String email : invites) {
            TextView row = new TextView(this);
            row.setText(getString(R.string.group_invite_pending, email));
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
                saveDraft();
            });
            emailChips.addView(row);
        }
    }

// f6: checks the name and asks the repository to create the group with the invites
    private void createGroup() {
        String name = GroupForm.cleanName(nameInput.getText().toString());
        if (!GroupForm.isValidName(name)) {
            nameInput.setError(getString(R.string.group_name_invalid,
                    GroupForm.MIN_NAME_LENGTH, GroupForm.MAX_NAME_LENGTH));
            nameInput.requestFocus();
            return;
        }
        if (!isTimeInTheFuture()) {
            return;
        }
// without connection we do not try, the draft is saved and the button comes back with the connection
        if (!online) {
            renderConnectivity();
            return;
        }
// f6: we turn off the button so the user can not create the same group two times
        creating = true;
        renderConnectivity();
        Repositories.groups().createGroup(name, DEFAULT_CATEGORY, new ArrayList<>(invites), new Callback<String>() {
            @Override
            public void onSuccess(String groupId) {
// the group exists now, so the draft is not needed anymore
                draftStore.clear();
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                toast(getString(R.string.group_created));
                startActivity(new Intent(CreateGroupActivity.this, GroupDetailActivity.class)
                        .putExtra(GroupDetailActivity.EXTRA_GROUP_ID, groupId));
                finish();
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                creating = false;
                renderConnectivity();
                toast(getString(R.string.group_create_failed));
            }
        });
    }

// short message at the bottom of the screen
    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
