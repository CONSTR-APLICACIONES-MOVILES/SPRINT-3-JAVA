package com.example.parchapp;

import android.app.DatePickerDialog;
import android.app.Dialog;
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
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
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
    // Schedule: length of the common slot, so the plan keeps it
    private static final String ARG_DURATION = "arg_duration";
    // bq3: position of the slot picked in Compare Availability, -1 when the form was opened without one
    private static final String ARG_SLOT_POSITION = "arg_slot_position";

    private String groupId;
    private int groupSize;

    public static CreateActivitySheet newInstance(String groupId, int groupSize, String slotTime,
                                                  int slotPosition) {
        CreateActivitySheet sheet = new CreateActivitySheet();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        args.putInt(ARG_GROUP_SIZE, groupSize);
        args.putString(ARG_SLOT_TIME, slotTime);
        args.putInt(ARG_SLOT_POSITION, slotPosition);
        sheet.setArguments(args);
        return sheet;
    }

    public CreateActivitySheet withSuggestedDate(String date) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putString(ARG_SLOT_DATE, date);
        setArguments(args);
        return this;
    }

    public CreateActivitySheet withSuggestedDuration(int minutes) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putInt(ARG_DURATION, minutes);
        setArguments(args);
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_create_activity, container, false);
    }

    // half open, the first tap on Create Activity only moved the sheet; fully open it goes to the button
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) dialog).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
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
        groupChip.setText(groupId == null ? "Loading your groups…" : "Group selected");
        int suggestedDuration = args.getInt(ARG_DURATION, 0);
        if (suggestedDuration >= 15 && suggestedDuration <= 720) {
            duration.setText(String.valueOf(suggestedDuration));
        }

        String suggestedDate = args.getString(ARG_SLOT_DATE);
        String suggestedTime = args.getString(ARG_SLOT_TIME);
        if (suggestedDate != null) date.setText(suggestedDate);
        // a slot from Compare Availability comes as a range ("5:30 PM – 11:00 PM"): we use its start, in the HH:mm the form expects
        int slotStart = com.example.parchapp.domain.PlanContext.startMinute(suggestedTime);
        if (slotStart >= 0) {
            time.setText(String.format(Locale.US, "%02d:%02d", slotStart / 60, slotStart % 60));
            // the slots are for today, so the date starts on today too
            if (suggestedDate == null) {
                SimpleDateFormat day = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                day.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
                date.setText(day.format(new java.util.Date()));
            }
        }
        date.setFocusable(false);
        date.setOnClickListener(v -> pickDate(date));
        time.setFocusable(false);
        time.setOnClickListener(v -> pickTime(time));

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        View createButton = view.findViewById(R.id.btn_create_activity);
        createButton.setEnabled(groupId != null && !groupId.trim().isEmpty());
        createButton.setOnClickListener(v -> publish(title, date, time, duration, location, createButton));
        if (groupId == null) {
            showGroupChoices(groupChip, createButton);
        }
    }

    /* Opened without a group (from Schedule): the organizer picks one of their groups, the plan is published for it. */
    private void showGroupChoices(TextView placeholder, View createButton) {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            placeholder.setText("Sign in to choose a group");
            return;
        }
        Repositories.groups().getGroups(user.getId(), new Callback<java.util.List<com.example.parchapp.domain.model.Group>>() {
            @Override public void onSuccess(java.util.List<com.example.parchapp.domain.model.Group> groups) {
                if (!isAdded()) return;
                if (groups.isEmpty()) {
                    placeholder.setText("Join a group to plan an activity");
                    return;
                }
                ViewGroup row = (ViewGroup) placeholder.getParent();
                row.removeAllViews();
                android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(requireContext());
                scroll.setHorizontalScrollBarEnabled(false);
                android.widget.LinearLayout chips = new android.widget.LinearLayout(requireContext());
                scroll.addView(chips);
                row.addView(scroll, new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                java.util.List<TextView> all = new java.util.ArrayList<>();
                for (com.example.parchapp.domain.model.Group group : groups) {
                    TextView chip = new TextView(requireContext());
                    chip.setText(group.getIcon().isEmpty() ? group.getName() : group.getIcon() + "  " + group.getName());
                    chip.setTextSize(12);
                    chip.setGravity(android.view.Gravity.CENTER);
                    int padding = Math.round(14 * getResources().getDisplayMetrics().density);
                    chip.setPadding(padding, 0, padding, 0);
                    android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, Math.round(38 * getResources().getDisplayMetrics().density));
                    params.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
                    chip.setOnClickListener(v -> {
                        groupId = group.getId();
                        groupSize = group.getSize();
                        for (TextView other : all) {
                            boolean on = other == chip;
                            other.setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                            other.setTextColor(requireContext().getColor(on ? R.color.brand_primary : R.color.brand_dark));
                        }
                        createButton.setEnabled(true);
                    });
                    chip.setBackgroundResource(R.drawable.bg_chip_unselected);
                    chip.setTextColor(requireContext().getColor(R.color.brand_dark));
                    all.add(chip);
                    chips.addView(chip, params);
                }
            }

            @Override public void onError(Exception error) {
                if (isAdded()) placeholder.setText("Could not load your groups");
            }
        });
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
                // bq9: a plan made from a Compare Availability slot was created after comparing
                boolean fromCompare = getArguments() != null && getArguments().getInt(ARG_SLOT_POSITION, -1) >= 0;
                event.put(AnalyticsEvents.PARAM_SOURCE, fromCompare
                        ? AnalyticsEvents.SOURCE_SUGGESTED_SLOT : AnalyticsEvents.SOURCE_GROUP_ACTIVITY);
                AnalyticsTracker.getInstance().track(AnalyticsEvents.ACTIVITY_CREATED, event);
                if (!isAdded()) return;
                Intent result = new Intent(requireContext(), ActivityCreatedActivity.class);
                result.putExtra(ActivityCreatedActivity.EXTRA_ACTIVITY_ID, saved.getId());
                int slotPosition = getArguments() == null ? -1 : getArguments().getInt(ARG_SLOT_POSITION, -1);
                if (slotPosition < 0 || Repositories.recommendationDecisions() == null) {
                    dismiss();
                    startActivity(result);
                    return;
                }
                prepareRecommendationOutcome(saved.getId(), dateInput.getText().toString().trim(), durationMinutes,
                        slotPosition, start, end, startIso, endIso, result);
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

    /* bq3: the plan came from a Compare slot, so Activity Created records whether the organizer kept the backend's suggestion. If the backend has nothing to compare with (another day, someone busy) the screen opens without it. */
    private void prepareRecommendationOutcome(String activityId, String date, int durationMinutes, int slotPosition,
                                              Calendar start, Calendar end, String startIso, String endIso,
                                              Intent result) {
        androidx.fragment.app.FragmentActivity host = requireActivity();
        Repositories.recommendationDecisions().prepareDecision(activityId, date, durationMinutes, slotPosition,
                start.getTimeInMillis(), end.getTimeInMillis(),
                new Callback<com.example.parchapp.data.RecommendationDecisionRepository.PreparedDecision>() {
                    @Override
                    public void onSuccess(com.example.parchapp.data.RecommendationDecisionRepository.PreparedDecision decision) {
                        if (decision != null) {
                            result.putExtra(ActivityCreatedActivity.EXTRA_RECOMMENDATION_ID, decision.recommendationId);
                            result.putExtra(ActivityCreatedActivity.EXTRA_WAS_MODIFIED, decision.wasModified);
                            result.putExtra(ActivityCreatedActivity.EXTRA_SLOT_POSITION, decision.position);
                            result.putExtra(ActivityCreatedActivity.EXTRA_SELECTED_START_TIME, startIso);
                            result.putExtra(ActivityCreatedActivity.EXTRA_SELECTED_END_TIME, endIso);
                            result.putExtra(ActivityCreatedActivity.EXTRA_GROUP_SIZE, groupSize);
                        }
                        open(host, result);
                    }

                    @Override
                    public void onError(Exception error) {
                        open(host, result);
                    }
                });
    }

    private void open(androidx.fragment.app.FragmentActivity host, Intent result) {
        if (host.isFinishing() || host.isDestroyed()) return;
        if (isAdded()) dismiss();
        host.startActivity(result);
    }

    // the picked values are Bogota time, so they must be written in Bogota time too, not in the phone's time zone
    private static SimpleDateFormat bogotaFormat(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
        return format;
    }

    private void pickDate(EditText field) {
        Calendar today = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        new DatePickerDialog(requireContext(), (picker, year, month, day) -> {
            Calendar chosen = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
            chosen.set(year, month, day);
            field.setText(bogotaFormat("yyyy-MM-dd").format(chosen.getTime()));
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(EditText field) {
        Calendar now = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
        new TimePickerDialog(requireContext(), (picker, hour, minute) -> {
            Calendar chosen = Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
            chosen.set(Calendar.HOUR_OF_DAY, hour);
            chosen.set(Calendar.MINUTE, minute);
            field.setText(bogotaFormat("HH:mm").format(chosen.getTime()));
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
