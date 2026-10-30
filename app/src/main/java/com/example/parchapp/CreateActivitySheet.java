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
import com.example.parchapp.analytics.CreationFlowTracker;
import com.example.parchapp.data.PendingActivityCreation;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
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
// bq3: start minute of each slot the organizer saw in compare availability, in the same order
    private static final String ARG_SHOWN_STARTS = "arg_shown_starts";
// keys to keep the group picked in the chips when the phone turns
    private static final String STATE_PICKED_GROUP = "picked_group";
    private static final String STATE_PICKED_SIZE = "picked_size";
// bq7: where the form was opened, and keys to keep the same flow when the phone turns
    private static final String ARG_ENTRY_POINT = "arg_entry_point";
    // bq9: the plan comes from a slot of a friends comparison in Schedule
    private static final String ARG_COMPARISON_ID = "arg_comparison_id";
    // bq9: who was compared and on which day, publishActivity needs them to accept the comparison
    private static final String ARG_COMPARED_IDS = "arg_compared_ids";
    private static final String ARG_COMPARED_DATE = "arg_compared_date";
    private static final String STATE_FLOW_ID = "flow_id";
    private static final String STATE_FLOW_STARTED = "flow_started";
    private static final String STATE_FLOW_STEPS = "flow_steps";
    private static final String STATE_FLOW_FINISHED = "flow_finished";

    private String groupId;
    private int groupSize;
// bq7: logs the steps the user reaches in this opening of the form
    private CreationFlowTracker flow;

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

// bq3: keeps the slots the organizer saw, so the backend measures the same list and the same positions
    public CreateActivitySheet withShownSlots(java.util.List<com.example.parchapp.domain.model.TimeSlot> shown) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        int[] starts = new int[shown.size()];
        for (int i = 0; i < starts.length; i++) {
            starts[i] = shown.get(i).getStartMinute();
        }
        args.putIntArray(ARG_SHOWN_STARTS, starts);
        setArguments(args);
        return this;
    }

// bq7: dashboard, group, slot or schedule, it goes in the creation_started event
    public CreateActivitySheet withComparisonId(@Nullable String comparisonId) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putString(ARG_COMPARISON_ID, comparisonId);
        setArguments(args);
        return this;
    }

    /* Schedule: the backend comparison of me and these friends on this day (a validated slot or a proposal). */
    public CreateActivitySheet withComparison(String comparisonId, java.util.List<String> friendIds, String date) {
        withComparisonId(comparisonId);
        Bundle args = getArguments();
        args.putStringArrayList(ARG_COMPARED_IDS, new java.util.ArrayList<>(friendIds));
        args.putString(ARG_COMPARED_DATE, date);
        setArguments(args);
        return this;
    }

    /* BQ9: the evidence sent with the plan. A Schedule comparison with its friends, direct() for a plan made without
     * comparing, and null (unknown) for a Compare Availability slot, which has no backend comparison to show. */
    @Nullable
    static ComparisonEvidence evidenceFor(int slotPosition, @Nullable String comparisonId,
                                          @Nullable java.util.List<String> friendIds, @Nullable String date,
                                          @Nullable String myId) {
        if (comparisonId != null && !comparisonId.isEmpty()) {
            return friendIds == null || friendIds.isEmpty() || myId == null
                    ? null : ComparisonEvidence.of(comparisonId, myId, friendIds, date);
        }
        return slotPosition >= 0 ? null : ComparisonEvidence.direct();
    }

    public CreateActivitySheet withEntryPoint(String entryPoint) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putString(ARG_ENTRY_POINT, entryPoint);
        setArguments(args);
        return this;
    }

    public CreateActivitySheet withSuggestedDuration(int minutes) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putInt(ARG_DURATION, minutes);
        setArguments(args);
        return this;
    }

    /* BQ9: true when the plan was made after comparing, from a Compare Availability slot or a Schedule comparison. */
    static boolean madeAfterComparing(int slotPosition, @Nullable String comparisonId) {
        return slotPosition >= 0 || (comparisonId != null && !comparisonId.isEmpty());
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

// bq7: one flow for each opening of the form, after the phone turns it is the same flow
        if (savedInstanceState != null && savedInstanceState.getString(STATE_FLOW_ID) != null) {
            flow = CreationFlowTracker.restore(savedInstanceState.getString(STATE_FLOW_ID),
                    savedInstanceState.getLong(STATE_FLOW_STARTED),
                    savedInstanceState.getStringArrayList(STATE_FLOW_STEPS),
                    savedInstanceState.getBoolean(STATE_FLOW_FINISHED));
        } else {
            flow = CreationFlowTracker.start(args.getString(ARG_ENTRY_POINT,
                    groupId != null ? AnalyticsEvents.ENTRY_GROUP : AnalyticsEvents.ENTRY_DASHBOARD));
        }
// a group that comes already picked means the user reached the group step when the form opened
        if (groupId != null) {
            flow.stepViewed(CreationFlowTracker.Step.GROUP);
        }

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        View createButton = view.findViewById(R.id.btn_create_activity);
        createButton.setEnabled(groupId != null && !groupId.trim().isEmpty());
        createButton.setOnClickListener(v -> publish(title, date, time, duration, location, createButton));
        if (groupId == null) {
// when the phone turns we bring back the group the user picked before
            String picked = savedInstanceState == null ? null : savedInstanceState.getString(STATE_PICKED_GROUP);
            if (picked != null) {
                groupId = picked;
                groupSize = savedInstanceState.getInt(STATE_PICKED_SIZE, 0);
                createButton.setEnabled(true);
            }
            showGroupChoices(groupChip, createButton, picked);
        }
    }

// bq7: the steps count when the user edits a field, we start listening after android puts back the texts
// so the texts that come already filled or come back after the phone turns are not counted
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        View view = getView();
        if (view == null) {
            return;
        }
        countFirstEdit(view.findViewById(R.id.input_activity_title), CreationFlowTracker.Step.DETAILS);
        countFirstEdit(view.findViewById(R.id.input_activity_duration), CreationFlowTracker.Step.DURATION);
        countFirstEdit(view.findViewById(R.id.input_activity_date), CreationFlowTracker.Step.DATE_TIME);
        countFirstEdit(view.findViewById(R.id.input_activity_time), CreationFlowTracker.Step.DATE_TIME);
        countFirstEdit(view.findViewById(R.id.input_activity_location), CreationFlowTracker.Step.PLACE);
    }

// the first change of the field marks its step, the tracker logs each step only one time
    private void countFirstEdit(EditText field, CreationFlowTracker.Step step) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override public void afterTextChanged(android.text.Editable s) {
                if (flow != null) flow.stepViewed(step);
            }
        });
    }

// the group picked in the chips is not in the arguments, so we save it before android makes the form again
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (flow != null) {
            outState.putString(STATE_FLOW_ID, flow.getFlowId());
            outState.putLong(STATE_FLOW_STARTED, flow.getStartedAt());
            outState.putStringArrayList(STATE_FLOW_STEPS, flow.seenSteps());
            outState.putBoolean(STATE_FLOW_FINISHED, flow.isFinished());
        }
        boolean pickedInChips = getArguments() == null || getArguments().getString(ARG_GROUP_ID) == null;
        if (pickedInChips && groupId != null) {
            outState.putString(STATE_PICKED_GROUP, groupId);
            outState.putInt(STATE_PICKED_SIZE, groupSize);
        }
    }

    /* Opened without a group (from Schedule): the organizer picks one of their groups, the plan is published for it. */
    /* BQ9: after comparing friends only the groups that have all of them can host the plan; if none does,
     * every group is shown and the plan goes without the comparison, so it is never blocked */
    private java.util.List<com.example.parchapp.domain.model.Group> groupsForComparison(
            java.util.List<com.example.parchapp.domain.model.Group> groups) {
        Bundle args = getArguments();
        java.util.List<String> compared = args == null ? null : args.getStringArrayList(ARG_COMPARED_IDS);
        if (compared == null || compared.isEmpty()) {
            return groups;
        }
        java.util.List<com.example.parchapp.domain.model.Group> fitting = new java.util.ArrayList<>();
        for (com.example.parchapp.domain.model.Group group : groups) {
            java.util.List<String> memberIds = new java.util.ArrayList<>();
            for (com.example.parchapp.domain.model.Member member : group.getMembers()) {
                memberIds.add(member.getId());
            }
            if (memberIds.containsAll(compared)) {
                fitting.add(group);
            }
        }
        if (fitting.isEmpty()) {
            args.remove(ARG_COMPARED_IDS);
            return groups;
        }
        return fitting;
    }

    private void showGroupChoices(TextView placeholder, View createButton, String pickedGroupId) {
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
                for (com.example.parchapp.domain.model.Group group : groupsForComparison(groups)) {
                    TextView chip = new TextView(requireContext());
// only the name of the group, without the emoji
                    chip.setText(group.getName());
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
                        flow.stepViewed(CreationFlowTracker.Step.GROUP);
                        for (TextView other : all) {
                            boolean on = other == chip;
                            other.setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                            other.setTextColor(requireContext().getColor(on ? R.color.brand_primary : R.color.brand_dark));
                        }
                        createButton.setEnabled(true);
                    });
                    boolean picked = group.getId().equals(pickedGroupId);
                    chip.setBackgroundResource(picked ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
                    chip.setTextColor(requireContext().getColor(picked ? R.color.brand_primary : R.color.brand_dark));
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
// bq7: tapping create activity is the review step, even if a field is wrong and the form stays open
        flow.stepViewed(CreationFlowTracker.Step.REVIEW);
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

        if (!Repositories.isFirebaseAvailable()) {
            Toast.makeText(requireContext(), "Activity publishing is unavailable right now", Toast.LENGTH_SHORT).show();
            return;
        }
        UserProfile currentUser = UserSession.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Sign in before creating an activity", Toast.LENGTH_SHORT).show();
            return;
        }
        button.setEnabled(false);
        String sessionId = UUID.randomUUID().toString();
        long startedAt = System.currentTimeMillis();
        ActivityCreationTelemetry.attempt(sessionId, "group_activity", groupSize);
        Bundle publishArgs = getArguments() == null ? new Bundle() : getArguments();
        ComparisonEvidence evidence = evidenceFor(publishArgs.getInt(ARG_SLOT_POSITION, -1),
                publishArgs.getString(ARG_COMPARISON_ID), publishArgs.getStringArrayList(ARG_COMPARED_IDS),
                publishArgs.getString(ARG_COMPARED_DATE), currentUser.getId());
        int slotPosition = publishArgs.getInt(ARG_SLOT_POSITION, -1);
        int[] shownStarts = publishArgs.getIntArray(ARG_SHOWN_STARTS);
        PendingActivityCreation pending = new PendingActivityCreation(currentUser.getId(),
                UUID.randomUUID().toString(), sessionId, startedAt, activity, evidence, slotPosition,
                durationMinutes, start.getTimeInMillis(), end.getTimeInMillis(), shownStarts, flow.getFlowId(),
                flow.getStartedAt(), flow.seenSteps(), PendingActivityCreation.State.PENDING,
                "Waiting to sync…", null, "");
        androidx.fragment.app.FragmentActivity host = requireActivity();
        Repositories.activityCreationSync().enqueue(pending,
                new com.example.parchapp.data.ActivityCreationSyncCoordinator.EnqueueCallback() {
                    @Override public void onSaved(PendingActivityCreation saved) {
                        if (host.isFinishing() || host.isDestroyed()) return;
                        Intent result = new Intent(host, ActivityCreatedActivity.class);
                        result.putExtra(ActivityCreatedActivity.EXTRA_PENDING_REQUEST_ID, saved.getRequestId());
                        open(host, result);
                    }

                    @Override public void onError(Exception error) {
                        ActivityCreationTelemetry.result(sessionId, "group_activity",
                                System.currentTimeMillis() - startedAt, false, null,
                                error.getClass().getSimpleName());
                        if (!isAdded()) return;
                        button.setEnabled(true);
                        Toast.makeText(requireContext(), error.getMessage() == null
                                ? "Could not save the activity for sync" : error.getMessage(), Toast.LENGTH_LONG).show();
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
