package com.example.parchapp;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.parchapp.analytics.ActivityCreationTelemetry;
import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

/** Creates an activity inside the caller-provided group, then opens the database-backed result view. */
public class CreateActivitySheet extends BottomSheetDialogFragment {
    private static final String ARG_GROUP_ID = "arg_group_id";
    private static final String ARG_GROUP_SIZE = "arg_group_size";
    private static final String ARG_SLOT_TIME = "arg_slot_time";
    private static final String ARG_SLOT_DATE = "arg_slot_date";

    private String groupId;
    private int groupSize;

    public static CreateActivitySheet newInstance(String groupId, int groupSize, String slotTime,
                                                  int slotPosition) {
        CreateActivitySheet sheet = new CreateActivitySheet();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putInt(ARG_GROUP_SIZE, groupSize);
        args.putString(ARG_SLOT_TIME, slotTime);
        sheet.setArguments(args);
        return sheet;
    }

    public CreateActivitySheet withSuggestedDate(String date) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putString(ARG_SLOT_DATE, date);
        setArguments(args);
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_create_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        groupId = args.getString(ARG_GROUP_ID);
        groupSize = args.getInt(ARG_GROUP_SIZE, 0);

        EditText title = view.findViewById(R.id.input_activity_title);
        EditText date = view.findViewById(R.id.input_activity_date);
        EditText time = view.findViewById(R.id.input_activity_time);
        EditText duration = view.findViewById(R.id.input_activity_duration);
        EditText location = view.findViewById(R.id.input_activity_location);
        TextView groupChip = view.findViewById(R.id.chip_group_eng);
        view.findViewById(R.id.chip_group_roomies).setVisibility(View.GONE);
        groupChip.setText(groupId == null ? "Open this form from a group" : "Group selected");

        String suggestedDate = args.getString(ARG_SLOT_DATE);
        String suggestedTime = args.getString(ARG_SLOT_TIME);
        if (suggestedDate != null) date.setText(suggestedDate);
        if (suggestedTime != null && !suggestedTime.contains("–") && !suggestedTime.contains("-")) {
            time.setText(suggestedTime);
        }
        date.setFocusable(false);
        date.setOnClickListener(v -> pickDate(date));
        time.setFocusable(false);
        time.setOnClickListener(v -> pickTime(time));

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        View createButton = view.findViewById(R.id.btn_create_activity);
        createButton.setEnabled(groupId != null && !groupId.trim().isEmpty());
        createButton.setOnClickListener(v -> publish(title, date, time, duration, location, createButton));
    }

    private void publish(EditText titleInput, EditText dateInput, EditText timeInput,
                         EditText durationInput, EditText locationInput, View button) {
        if (groupId == null || groupId.trim().isEmpty()) {
            Toast.makeText(requireContext(), "Choose a group before creating the activity", Toast.LENGTH_SHORT).show();
            return;
        }
        String title = titleInput.getText().toString().trim();
        if (title.isEmpty()) {
            titleInput.setError("Enter an activity title");
            return;
        }
        int durationMinutes;
        try {
            durationMinutes = Integer.parseInt(durationInput.getText().toString().trim());
        } catch (NumberFormatException error) {
            durationInput.setError("Enter a duration in minutes");
            return;
        }
        if (durationMinutes < 15 || durationMinutes > 720) {
            durationInput.setError("Duration must be between 15 and 720 minutes");
            return;
        }
        Calendar start = parseStart(dateInput.getText().toString(), timeInput.getText().toString());
        if (start == null || !start.getTime().after(Calendar.getInstance(
                TimeZone.getTimeZone("America/Bogota")).getTime())) {
            timeInput.setError("Choose a valid future date and time");
            return;
        }
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MINUTE, durationMinutes);
        String startIso = toUtcIso(start);
        String endIso = toUtcIso(end);
        PlannedActivity activity = new PlannedActivity(null, groupId, title, "other",
                dateInput.getText().toString().trim(), timeInput.getText().toString().trim(),
                locationInput.getText().toString().trim(), PlannedActivity.STATUS_PROPOSED,
                System.currentTimeMillis(), groupSize, false).withSchedule(startIso, endIso);

        button.setEnabled(false);
        String sessionId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        ActivityCreationTelemetry.attempt(sessionId, "group_activity", groupSize);
        Repositories.activities().createActivity(activity, new Callback<PlannedActivity>() {
            @Override public void onSuccess(PlannedActivity saved) {
                ActivityCreationTelemetry.result(sessionId, "group_activity",
                        System.currentTimeMillis() - startedAt, true, saved.getId(), null);
                Map<String, Object> event = new HashMap<>();
                event.put(AnalyticsEvents.PARAM_ACTIVITY_ID, saved.getId());
                event.put(AnalyticsEvents.PARAM_CREATION_SESSION_ID, sessionId);
                event.put(AnalyticsEvents.PARAM_CATEGORY, saved.getCategory());
                event.put(AnalyticsEvents.PARAM_GROUP_SIZE, groupSize);
                event.put(AnalyticsEvents.PARAM_SOURCE, "group_activity");
                AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CREATED, event);
                if (!isAdded()) return;
                Intent result = new Intent(requireContext(), ActivityCreatedActivity.class);
                result.putExtra(ActivityCreatedActivity.EXTRA_ACTIVITY_ID, saved.getId());
                dismiss();
                startActivity(result);
            }

            @Override public void onError(Exception error) {
                ActivityCreationTelemetry.result(sessionId, "group_activity",
                        System.currentTimeMillis() - startedAt, false, null,
                        error.getClass().getSimpleName());
                button.setEnabled(true);
                Toast.makeText(requireContext(), "Could not create the activity", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickDate(EditText field) {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
            chosen.set(year, month, day);
            field.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(chosen.getTime()));
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(EditText field) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
            Calendar chosen = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
            chosen.set(Calendar.HOUR_OF_DAY, hour);
            chosen.set(Calendar.MINUTE, minute);
            field.setText(new SimpleDateFormat("HH:mm", Locale.US).format(chosen.getTime()));
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show();
    }

    private Calendar parseStart(String date, String time) {
        String value = date.trim() + " " + time.trim();
        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        parser.setLenient(false);
        parser.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
        ParsePosition position = new ParsePosition(0);
        java.util.Date parsed = parser.parse(value, position);
        if (parsed == null || position.getIndex() != value.length()) return null;
        Calendar result = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        result.setTime(parsed);
        return result;
    }

    private String toUtcIso(Calendar time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(time.getTime());
    }
}
