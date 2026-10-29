package com.example.parchapp;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.AnalyticsEvents;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.data.ScheduleRepository;
import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.Callback;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/* Shared weekly schedule: my busy blocks next to the selected friends', with the slots where
   everyone is free for the chosen duration. Only busy intervals are shown, never event details. */
public class ScheduleActivity extends AppCompatActivity {
    private static final String TAG = "ScheduleActivity";
    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");
    private static final int[] DURATIONS = {30, 45, 60, 120};
    private static final int DEFAULT_SELECTED_FRIENDS = 2;
    private static final int START = 8 * 60;
    private static final int END = 23 * 60;

    private final ScheduleRepository repository = Repositories.schedules();
    private final List<ScheduleFriend> friends = new ArrayList<>();
    private final List<String> selectedIds = new ArrayList<>();
    private final List<Calendar> weekDays = new ArrayList<>();
    private List<DaySchedule> schedules = new ArrayList<>();
    private List<TimeSlot> slots = new ArrayList<>();
    private boolean comparing = true;
    private int durationMinutes = 60;
    private int selectedDay;
    private int selectedSlot;
    private int loadRequest;

    private LinearLayout chips;
    private LinearLayout durations;
    private LinearLayout days;
    private FrameLayout laneMe;
    private FrameLayout laneFriends;
    private FrameLayout slotLayer;
    private TextView compareButton;
    private TextView friendsHeader;
    private TextView stateMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        // bq8: schedule counts as used when this screen opens (not again on rotation)
        if (savedInstanceState == null) {
            AnalyticsTracker.getInstance().trackFeature(AnalyticsEvents.FEATURE_SCHEDULE, "schedule");
        }

        chips = findViewById(R.id.schedule_chips);
        durations = findViewById(R.id.schedule_durations);
        days = findViewById(R.id.schedule_days);
        laneMe = findViewById(R.id.schedule_lane_me);
        laneFriends = findViewById(R.id.schedule_lane_friends);
        slotLayer = findViewById(R.id.schedule_slot_layer);
        compareButton = findViewById(R.id.schedule_compare_btn);
        friendsHeader = findViewById(R.id.schedule_friends_header);
        stateMessage = findViewById(R.id.schedule_state_message);

        UserProfile currentUser = UserSession.getCurrentUser();
        ((TextView) findViewById(R.id.schedule_profile_btn)).setText(
                currentUser == null ? "" : currentUser.getInitials());
        findViewById(R.id.schedule_profile_btn).setOnClickListener(v ->
                new AvailabilityStatusSheet().show(getSupportFragmentManager(), "availability_status"));
        findViewById(R.id.schedule_search_btn).setOnClickListener(v -> toast("Search coming soon"));
        findViewById(R.id.schedule_notif_btn).setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        compareButton.setOnClickListener(v -> toggleCompare());
        findViewById(R.id.schedule_summary).setOnClickListener(v -> openSlotsSheet());
        findViewById(R.id.schedule_fab).setOnClickListener(v ->
                new CreateActivitySheet().withSuggestedDate(selectedDateLabel())
                        .show(getSupportFragmentManager(), "create_activity"));
// shared bottom bar, schedule is the active tab
        BottomNav.setup(this, BottomNav.Tab.SCHEDULE);

        buildWeek();
        buildTimeColumnAndGrid();
        renderDurations();
        renderDays();
        renderChips();
        loadFriends();
    }

    //DATA (dn't overseeeee)

    private void loadFriends() {
        repository.getFriends(new Callback<List<ScheduleFriend>>() {
            @Override
            public void onSuccess(List<ScheduleFriend> result) {
                friends.clear();
                friends.addAll(result);
                for (int i = 0; i < friends.size() && selectedIds.size() < DEFAULT_SELECTED_FRIENDS; i++) {
                    selectedIds.add(friends.get(i).getId());
                }
                renderChips();
                loadDay();
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Loading schedule friends failed", error);
                showState("Could not load your friends. Showing only your schedule.");
                loadDay();
            }
        });
    }

    private void loadDay() {
        int request = ++loadRequest;
        List<String> ids = comparing ? new ArrayList<>(selectedIds) : new ArrayList<>();
        repository.getDay(selectedDateKey(), ids, new Callback<List<DaySchedule>>() {
            @Override
            public void onSuccess(List<DaySchedule> result) {
                if (request != loadRequest || isFinishing()) {
                    return; // an older day or selection; a newer request is on its way
                }
                schedules = mineFirst(result, ids);
                computeSlots();
                renderTimeline();
            }

            @Override
            public void onError(Exception error) {
                Log.w(TAG, "Loading the day failed", error);
                if (request != loadRequest || isFinishing()) {
                    return;
                }
                schedules = new ArrayList<>();
                slots = new ArrayList<>();
                renderTimeline();
                showState("Could not load schedules. Check your connection and try again.");
            }
        });
    }

    /* Puts my schedule first and keeps only the requested friends, matching by owner ID rather than trusting the order of the response. A friend missing from it becomes unknown. */
    private List<DaySchedule> mineFirst(List<DaySchedule> result, List<String> friendIds) {
        Map<String, DaySchedule> byOwner = new HashMap<>();
        for (DaySchedule schedule : result) {
            byOwner.put(schedule.getOwnerId(), schedule);
        }
        UserProfile current = UserSession.getCurrentUser();
        String me = current == null ? "" : current.getId();
        List<DaySchedule> ordered = new ArrayList<>();
        DaySchedule mine = byOwner.get(me);
        ordered.add(mine != null ? mine : new DaySchedule(me, false, new ArrayList<>()));
        for (String id : friendIds) {
            DaySchedule theirs = byOwner.get(id);
            ordered.add(theirs != null ? theirs : new DaySchedule(id, false, new ArrayList<>()));
        }
        return ordered;
    }

    private void computeSlots() {
        slots = new ArrayList<>();
        selectedSlot = 0;
    }

    //Header rows

    private void renderChips() {
        chips.removeAllViews();
        TextView me = chip(getString(R.string.me_chip), R.drawable.bg_primary_pill, Color.WHITE);
        me.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0);
        me.setCompoundDrawableTintList(ColorStateList.valueOf(Color.WHITE));
        chips.addView(me);

        for (String id : selectedIds) {
            ScheduleFriend friend = friend(id);
            if (friend == null) {
                continue;
            }
            TextView chip = chip(friend.getShortName() + "  ✕", R.drawable.bg_schedule_chip_friend,
                    Color.parseColor("#0B1C30"));
            chip.setContentDescription("Remove " + friend.getShortName());
            chip.setOnClickListener(v -> {
                selectedIds.remove(id);
                renderChips();
                loadDay();
            });
            chips.addView(chip);
        }

        TextView add = chip(getString(R.string.add_friends), R.drawable.bg_schedule_chip_add,
                getColor(R.color.brand_primary));
        add.setOnClickListener(v -> openFriendPicker());
        chips.addView(add);

        int count = selectedIds.size() + 1;
        boolean active = comparing && !selectedIds.isEmpty();
        compareButton.setText(active ? "Comparing (" + count + ")" : "Compare (" + count + ")");
        compareButton.setBackgroundResource(active ? R.drawable.bg_button_rounded_primary : R.drawable.bg_schedule_compare_off);
        int textColor = active ? Color.WHITE : getColor(R.color.brand_primary);
        compareButton.setTextColor(textColor);
        compareButton.setCompoundDrawableTintList(ColorStateList.valueOf(textColor));

        List<String> names = new ArrayList<>();
        for (String id : selectedIds) {
            ScheduleFriend friend = friend(id);
            if (friend != null) {
                names.add(friend.getShortName());
            }
        }
        friendsHeader.setText(!active ? "Friends hidden"
                : names.size() <= 2 ? String.join(" & ", names) : names.get(0) + " +" + (names.size() - 1));
    }

    private void toggleCompare() {
        if (selectedIds.isEmpty()) {
            openFriendPicker();
            toast("Select at least one friend to compare");
            return;
        }
        comparing = !comparing;
        renderChips();
        loadDay();
    }

    private void renderDurations() {
        durations.removeAllViews();
        boolean custom = true;
        for (int minutes : DURATIONS) {
            boolean on = minutes == durationMinutes;
            custom &= !on;
            TextView chip = durationChip(minutes < 60 ? minutes + "m" : (minutes / 60) + "h", on);
            chip.setOnClickListener(v -> setDuration(minutes));
            durations.addView(chip);
        }
        TextView customChip = durationChip(custom ? durationMinutes + "m" : getString(R.string.custom_duration), custom);
        if (!custom) {
            customChip.setBackgroundResource(R.drawable.bg_schedule_duration_custom);
            customChip.setTextColor(getColor(R.color.brand_primary));
        }
        customChip.setOnClickListener(v -> openCustomDuration());
        durations.addView(customChip);
    }

    private void setDuration(int minutes) {
        durationMinutes = minutes;
        renderDurations();
        computeSlots();
        renderTimeline();
    }

    private void openCustomDuration() {
        NumberPicker picker = new NumberPicker(this);
        String[] labels = new String[16];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = ((i + 1) * 15) + " min";
        }
        picker.setMinValue(0);
        picker.setMaxValue(labels.length - 1);
        picker.setDisplayedValues(labels);
        picker.setValue(Math.max(0, Math.min(labels.length - 1, durationMinutes / 15 - 1)));
        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_duration_title)
                .setView(picker)
                .setPositiveButton(R.string.done, (d, w) -> setDuration((picker.getValue() + 1) * 15))
                .setNegativeButton(R.string.close_label, null)
                .show();
    }

    private void buildWeek() {
        Calendar today = Calendar.getInstance(BOGOTA);
        int dayOfWeek = today.get(Calendar.DAY_OF_WEEK);
        Calendar monday = (Calendar) today.clone();
        if (dayOfWeek == Calendar.SATURDAY) {
            monday.add(Calendar.DAY_OF_MONTH, 2);
        } else if (dayOfWeek == Calendar.SUNDAY) {
            monday.add(Calendar.DAY_OF_MONTH, 1);
        } else {
            monday.add(Calendar.DAY_OF_MONTH, Calendar.MONDAY - dayOfWeek);
        }
        for (int i = 0; i < 5; i++) {
            Calendar day = (Calendar) monday.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            weekDays.add(day);
        }
        selectedDay = dayOfWeek >= Calendar.MONDAY && dayOfWeek <= Calendar.FRIDAY ? dayOfWeek - Calendar.MONDAY : 0;
        ((TextView) findViewById(R.id.schedule_week_label)).setText("Week of " + format("MMM d", monday));
    }

    private void renderDays() {
        days.removeAllViews();
        for (int i = 0; i < weekDays.size(); i++) {
            Calendar day = weekDays.get(i);
            boolean on = i == selectedDay;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(10), dp(4), dp(10), dp(4));
            if (on) {
                tab.setBackgroundResource(R.drawable.bg_schedule_day_on);
            }
            TextView name = text(format("EEE", day).toUpperCase(Locale.US), 10, true,
                    on ? Color.parseColor("#B2C5FF") : Color.parseColor("#737685"));
            name.setLetterSpacing(0.08f);
            TextView number = text(format("d", day), 12, true, on ? Color.WHITE : Color.parseColor("#434654"));
            tab.addView(name);
            tab.addView(number);
            int index = i;
            tab.setOnClickListener(v -> {
                selectedDay = index;
                renderDays();
                loadDay();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginStart(dp(2));
            days.addView(tab, params);
        }
    }

    // ---------- Timeline ----------

    private void buildTimeColumnAndGrid() {
        FrameLayout timeColumn = findViewById(R.id.schedule_time_column);
        FrameLayout grid = findViewById(R.id.schedule_grid);
        for (int minute = START; minute <= END; minute += 60) {
            TextView label = text(TimeSlot.formatMinute(minute), 11, true, Color.parseColor("#737685"));
            label.setGravity(Gravity.END);
            FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = Math.max(0, dp(minute - START) - dp(7));
            labelParams.setMarginEnd(dp(8));
            timeColumn.addView(label, labelParams);

            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E2E8F0"));
            FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            lineParams.topMargin = dp(minute - START);
            grid.addView(line, 0, lineParams);
        }
    }

    private void renderTimeline() {
        laneMe.removeAllViews();
        laneFriends.removeAllViews();
        slotLayer.removeAllViews();
        List<String> warnings = new ArrayList<>();

        if (!schedules.isEmpty()) {
            DaySchedule mine = schedules.get(0);
            if (!mine.isKnown()) {
                warnings.add("you");
            }
            for (TimeBlock block : mine.getBusy()) {
                addBlock(laneMe, block, "Busy", TimeSlot.formatMinute(block.getStartMinute()) + " – "
                        + TimeSlot.formatMinute(block.getEndMinute()), R.drawable.bg_schedule_my_event,
                        getColor(R.color.brand_primary));
            }
            for (int i = 1; i < schedules.size(); i++) {
                DaySchedule theirs = schedules.get(i);
                ScheduleFriend friend = friend(theirs.getOwnerId());
                String name = friend != null ? friend.getShortName() : "Friend";
                if (!theirs.isKnown()) {
                    warnings.add(name);
                }
                for (TimeBlock block : theirs.getBusy()) {
                    addBlock(laneFriends, block, name, null, R.drawable.bg_schedule_busy, Color.parseColor("#334155"));
                }
            }
        }

        for (int i = 0; i < slots.size(); i++) {
            TimeSlot slot = slots.get(i);
            View view = new View(this);
            view.setBackgroundResource(i == selectedSlot ? R.drawable.bg_schedule_slot_selected : R.drawable.bg_schedule_slot);
            view.setAlpha(0.85f);
            view.setContentDescription("Common free slot " + slot.formatRange());
            FrameLayout.LayoutParams params = blockParams(slot.getStartMinute(), slot.getEndMinute());
            params.topMargin += dp(2);
            params.height -= dp(4);
            int index = i;
            view.setOnClickListener(v -> {
                selectedSlot = index;
                renderTimeline();
            });
            slotLayer.addView(view, params);
        }
        addNowIndicator();

        if (warnings.isEmpty()) {
            showState("Common-slot recommendations are not included in this base branch.");
        } else {
            showState("No synced calendar for " + String.join(", ", warnings)
                    + " on this day. Unknown is not counted as free.");
        }
        renderSummary();
    }

    private void addBlock(FrameLayout lane, TimeBlock block, String title, String subtitle, int background, int textColor) {
        int start = Math.max(START, block.getStartMinute());
        int end = Math.min(END, block.getEndMinute());
        if (start >= end) {
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(background);
        box.setPadding(dp(8), dp(4), dp(6), dp(4));
        box.addView(text(title, 12, true, textColor));
        if (subtitle != null && end - start >= 45) {
            box.addView(text(subtitle, 10, false, Color.parseColor("#434654")));
        }
        lane.addView(box, blockParams(start, end));
    }

    private FrameLayout.LayoutParams blockParams(int start, int end) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(Math.min(END, end) - Math.max(START, start)));
        params.topMargin = dp(Math.max(START, start) - START);
        params.setMarginStart(dp(6));
        params.setMarginEnd(dp(6));
        return params;
    }

    private void addNowIndicator() {
        int now = nowMinute();
        if (!isTodaySelected() || now < START || now > END) {
            return;
        }
        LinearLayout indicator = new LinearLayout(this);
        indicator.setGravity(Gravity.CENTER_VERTICAL);
        View line = new View(this);
        line.setBackgroundColor(Color.parseColor("#F43F5E"));
        indicator.addView(line, new LinearLayout.LayoutParams(0, dp(2), 1));
        TextView label = text(TimeSlot.formatMinute(now), 9, true, Color.WHITE);
        label.setBackgroundResource(R.drawable.bg_schedule_now_label);
        label.setPadding(dp(5), dp(1), dp(5), dp(1));
        indicator.addView(label);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(now - START) - dp(7);
        slotLayer.addView(indicator, params);
        ScrollView scroll = findViewById(R.id.schedule_scroll);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, params.topMargin - dp(120))));
    }

    private void renderSummary() {
        TextView title = findViewById(R.id.schedule_summary_title);
        TextView desc = findViewById(R.id.schedule_summary_desc);
        if (slots.isEmpty()) {
            title.setText("No common slots");
            desc.setText("Slot recommendation feature is not included in this base branch.");
            return;
        }
        title.setText(slots.size() + (slots.size() == 1 ? " common slot found  ⌃" : " common slots found  ⌃"));
        desc.setText((selectedSlot == 0 ? "Best option: " : "Selected: ") + slots.get(selectedSlot).formatRange());
    }

    //Sheets

    private void openFriendPicker() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = sheetRoot(getString(R.string.select_friends_title), getString(R.string.select_friends_subtitle));
        List<String> picked = new ArrayList<>(selectedIds);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (friends.isEmpty()) {
            list.addView(text("Join a group to compare schedules with its members.", 12, false,
                    Color.parseColor("#434654")));
        }
        for (ScheduleFriend friend : friends) {
            CheckBox row = new CheckBox(this);
            row.setText(friend.getName());
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.setChecked(picked.contains(friend.getId()));
            row.setButtonTintList(ColorStateList.valueOf(getColor(R.color.brand_primary)));
            row.setPadding(dp(4), dp(8), dp(4), dp(8));
            row.setOnCheckedChangeListener((button, checked) -> {
                if (checked && picked.size() >= ScheduleRepository.MAX_FRIENDS) {
                    button.setChecked(false);
                    toast("Compare up to " + ScheduleRepository.MAX_FRIENDS + " friends at a time");
                } else if (checked) {
                    picked.add(friend.getId());
                } else {
                    picked.remove(friend.getId());
                }
            });
            list.addView(row);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        root.addView(buttonRow(getString(R.string.done), sheet::dismiss, getString(R.string.view_comparison), () -> {
            selectedIds.clear();
            selectedIds.addAll(picked);
            comparing = true;
            renderChips();
            loadDay();
            sheet.dismiss();
        }, R.drawable.bg_button_rounded_primary));
        sheet.setContentView(root);
        sheet.show();
    }

    private void openSlotsSheet() {
        if (slots.isEmpty()) {
            toast("No common slots for this day");
            return;
        }
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = sheetRoot(getString(R.string.available_common_slots),
                "For " + format("EEEE, MMM d", weekDays.get(selectedDay)) + " • " + durationLabel());
        RadioGroup group = new RadioGroup(this);
        for (int i = 0; i < slots.size(); i++) {
            TimeSlot slot = slots.get(i);
            RadioButton option = new RadioButton(this);
            option.setId(View.generateViewId());
            option.setText(slot.formatRange() + (i == 0 ? "   •  Best option" : "")
                    + "\n" + slot.getFreeCount() + " of " + slot.getGroupSize() + " schedules free");
            option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            option.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#059669")));
            option.setPadding(dp(4), dp(8), dp(4), dp(8));
            option.setTag(i);
            group.addView(option);
            if (i == selectedSlot) {
                option.setChecked(true);
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(group);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(buttonRow(getString(R.string.close_label), sheet::dismiss, getString(R.string.plan_with_slot), () -> {
            View checked = group.findViewById(group.getCheckedRadioButtonId());
            selectedSlot = checked != null ? (int) checked.getTag() : 0;
            sheet.dismiss();
            renderTimeline();
            planWithSelectedSlot();
        }, R.drawable.bg_schedule_slot_plan));
        sheet.setContentView(root);
        sheet.show();
    }

    private void planWithSelectedSlot() {
        TimeSlot slot = slots.get(selectedSlot);
        CreateActivitySheet.newInstance(null, schedules.size(), slot.formatRange(), -1)
                .withSuggestedDate(selectedDateLabel())
                .show(getSupportFragmentManager(), "create_activity");
    }

    //The Small view helpers 

    private LinearLayout sheetRoot(String title, String subtitle) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(20));
        root.setBackgroundColor(Color.WHITE);
        root.addView(text(title, 16, true, Color.parseColor("#0B1C30")));
        TextView sub = text(subtitle, 12, false, Color.parseColor("#434654"));
        sub.setPadding(0, dp(2), 0, dp(10));
        root.addView(sub);
        return root;
    }

    private LinearLayout buttonRow(String secondary, Runnable onSecondary, String primary, Runnable onPrimary,
                                   int primaryBackground) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(14), 0, 0);
        AppCompatButton left = new AppCompatButton(this);
        left.setText(secondary);
        left.setAllCaps(false);
        left.setBackgroundResource(R.drawable.bg_card_outline_16);
        left.setOnClickListener(v -> onSecondary.run());
        AppCompatButton right = new AppCompatButton(this);
        right.setText(primary);
        right.setAllCaps(false);
        right.setTypeface(right.getTypeface(), Typeface.BOLD);
        right.setTextColor(Color.WHITE);
        right.setBackgroundResource(primaryBackground);
        right.setOnClickListener(v -> onPrimary.run());
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        leftParams.setMarginEnd(dp(8));
        row.addView(left, leftParams);
        row.addView(right, new LinearLayout.LayoutParams(0, dp(46), 1));
        return row;
    }

    private TextView chip(String label, int background, int textColor) {
        TextView chip = text(label, 12, true, textColor);
        chip.setBackgroundResource(background);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setCompoundDrawablePadding(dp(4));
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        chip.setLayoutParams(params);
        return chip;
    }

    private TextView durationChip(String label, boolean on) {
        TextView chip = text(label, 12, true, on ? Color.WHITE : Color.parseColor("#434654"));
        chip.setBackgroundResource(on ? R.drawable.bg_schedule_duration_on : R.drawable.bg_schedule_duration_off);
        chip.setPadding(dp(10), dp(4), dp(10), dp(4));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(dp(4));
        chip.setLayoutParams(params);
        return chip;
    }

    private TextView text(String value, int sp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setSingleLine(false);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT_BOLD);
        }
        return view;
    }

    private void showState(String message) {
        stateMessage.setText(message);
        stateMessage.setVisibility(View.VISIBLE);
    }

    private ScheduleFriend friend(String id) {
        for (ScheduleFriend friend : friends) {
            if (friend.getId().equals(id)) {
                return friend;
            }
        }
        return null;
    }

    private String durationLabel() {
        return durationMinutes % 60 == 0 ? (durationMinutes / 60) + " h" : durationMinutes + " min";
    }

    private String selectedDateKey() {
        return format("yyyy-MM-dd", weekDays.get(selectedDay));
    }

    private String selectedDateLabel() {
        return format("EEE, MMM d, yyyy", weekDays.get(selectedDay));
    }

    private boolean isTodaySelected() {
        return selectedDateKey().equals(format("yyyy-MM-dd", Calendar.getInstance(BOGOTA)));
    }

    private int nowMinute() {
        Calendar now = Calendar.getInstance(BOGOTA);
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static String format(String pattern, Calendar day) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(BOGOTA);
        return format.format(day.getTime());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
