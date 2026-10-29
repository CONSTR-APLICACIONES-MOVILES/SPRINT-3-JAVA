package com.example.parchapp;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.analytics.AnalyticsEvents;
import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.OfflineScheduleRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.data.ScheduleRepository;
import com.example.parchapp.domain.availability.CommonSlotFinder;
import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.GoogleCalendarImport;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/* Schedule, the same screen as the Flutter ScheduleView: compare my busy blocks with the selected friends', pick a duration and a day of 2026, import Google Calendar, and plan on a common slot after the backend checks it is still free. Java extras kept: the Comparing on/off switch, the now line, tapping slots on the timeline, warnings for calendars that are not synced and the 10 friends limit. Only busy intervals are shown, never event details. */
public class ScheduleActivity extends AppCompatActivity {
    private static final String TAG = "ScheduleActivity";
    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");
    private static final int[] QUICK_DURATIONS = {30, 45, 60, 120};
    private static final int DEFAULT_SELECTED_FRIENDS = 2;
    // the timeline runs 8 AM to 11 PM (the Java team keeps the evening), 1dp per minute like Flutter
    private static final int START = CommonSlotFinder.DAY_START_MINUTE;
    private static final int END = CommonSlotFinder.DAY_END_MINUTE;
    private static final int MIN_BLOCK_DP = 24;
    private static final int YEAR = 2026;
    private static final int MAX_HOURS = 12;

    private enum Status { LOADING, READY, ERROR }

    private enum Import { IDLE, IMPORTING, IMPORTED, ERROR }

    private final ScheduleRepository repository = Repositories.schedules();
    private final List<ScheduleFriend> friends = new ArrayList<>();
    private final List<String> selectedIds = new ArrayList<>();
    private List<DaySchedule> schedules = new ArrayList<>();
    private List<TimeSlot> slots = new ArrayList<>();
    private Calendar selectedDay;
    private int durationMinutes = 60;
    private int selectedSlot = -1;
    private boolean comparing = true;
    private boolean loadingFriends;
    private boolean planning;
    private int requestVersion;
    private Status status = Status.LOADING;
    private String error;
    private Import importStatus = Import.IDLE;
    private String importMessage;

    private GoogleCalendarImport calendarImport;
    private LinearLayout chips;
    private LinearLayout durations;
    private LinearLayout days;
    private FrameLayout laneMe;
    private FrameLayout laneFriends;
    private FrameLayout slotLayer;
    private TextView compareButton;
    private TextView friendsHeader;
    private TextView stateMessage;
    private EditText hoursInput;
    private EditText minutesInput;
    private BottomSheetDialog slotsSheet;
    private Runnable refreshSlotsSheet;

// in landscape the controls on top took all the height and the hours had no space
// so in landscape the controls and the hours scroll together, and the hours keep the height of the screen
// in portrait nothing changes
    private void fitLandscape() {
        if (getResources().getConfiguration().orientation != android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }
        LinearLayout root = (LinearLayout) ((ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
// the header is the first view and the bottom bar the last one, everything between them moves to the column
        while (root.getChildCount() > 2) {
            View child = root.getChildAt(1);
            root.removeViewAt(1);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            if (params.weight > 0) {
// the hours used the free height, now they use the height of the screen
                params.height = getResources().getDisplayMetrics().heightPixels;
                params.weight = 0;
            }
            column.addView(child, params);
        }
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, 1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
// the hours have their own scroll, it works together with the scroll of the screen
        findViewById(R.id.schedule_scroll).setNestedScrollingEnabled(true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);
        fitLandscape();
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
        hoursInput = findViewById(R.id.schedule_hours);
        minutesInput = findViewById(R.id.schedule_minutes);

        // shared top bar with the logo, search, alerts and the user initials
        TopHeader.setup(this);
// shared bottom bar, schedule is the active tab
        BottomNav.setup(this, BottomNav.Tab.SCHEDULE);

        calendarImport = new GoogleCalendarImport(this, new Callback<Integer>() {
            @Override
            public void onSuccess(Integer count) {
                importStatus = Import.IMPORTED;
                importMessage = count + " busy intervals synced.";
                renderImport();
                refreshDay();
            }

            @Override
            public void onError(Exception failure) {
                Log.w(TAG, "Google Calendar import failed", failure);
                importStatus = Import.ERROR;
                importMessage = "Unable to import Google Calendar.";
                renderImport();
            }
        });

        compareButton.setOnClickListener(v -> toggleCompare());
        findViewById(R.id.schedule_import_btn).setOnClickListener(v -> importGoogleCalendar());
        findViewById(R.id.schedule_prev_week).setOnClickListener(v -> changeWeek(-1));
        findViewById(R.id.schedule_next_week).setOnClickListener(v -> changeWeek(1));
        findViewById(R.id.schedule_week_picker).setOnClickListener(v -> openCalendarPicker());
        findViewById(R.id.schedule_calendar_btn).setOnClickListener(v -> openCalendarPicker());
        findViewById(R.id.schedule_summary).setOnClickListener(v -> openSlotsSheet());
        findViewById(R.id.schedule_fab).setOnClickListener(v -> planActivity());
        findViewById(R.id.schedule_retry).setOnClickListener(v -> load());
        setUpStepper();

        selectedDay = todayInBogota();
        buildTimeColumnAndGrid();
        render();
        load();
    }

    // ---------- Data (like the Flutter ScheduleViewModel) ----------

    private void load() {
        if (loadingFriends) {
            return;
        }
        loadingFriends = true;
        status = Status.LOADING;
        render();
        repository.getFriends(new Callback<List<ScheduleFriend>>() {
            @Override
            public void onSuccess(List<ScheduleFriend> result) {
                loadingFriends = false;
                if (isFinishing()) {
                    return;
                }
                friends.clear();
                friends.addAll(result);
                selectedIds.clear();
                for (int i = 0; i < friends.size() && selectedIds.size() < DEFAULT_SELECTED_FRIENDS; i++) {
                    selectedIds.add(friends.get(i).getId());
                }
                refreshDay();
            }

            @Override
            public void onError(Exception failure) {
                loadingFriends = false;
                Log.w(TAG, "Loading schedule friends failed", failure);
                // Java team: without friends we still show my own schedule instead of a full error
                refreshDay();
                toast("Could not load your friends. Showing only your schedule.");
            }
        });
    }

    private void refreshDay() {
        int version = ++requestVersion;
        List<String> ids = comparing ? new ArrayList<>(selectedIds) : new ArrayList<>();
        String date = dateKey(selectedDay);
        status = Status.LOADING;
        error = null;
        schedules = new ArrayList<>();
        slots = new ArrayList<>();
        selectedSlot = -1;
        render();
        repository.getDay(date, ids, new Callback<List<DaySchedule>>() {
            @Override
            public void onSuccess(List<DaySchedule> result) {
                // an older day or selection answered late; a newer request is on its way
                if (version != requestVersion || isFinishing()) {
                    return;
                }
                schedules = mineFirst(result, ids);
                computeSlots();
                status = Status.READY;
                render();
                scrollToFocus();
            }

            @Override
            public void onError(Exception failure) {
                Log.w(TAG, "Loading the day failed", failure);
                if (version != requestVersion || isFinishing()) {
                    return;
                }
                status = Status.ERROR;
                error = "Unable to compare availability.";
                render();
            }
        });
    }

    /* Puts my schedule first and keeps only the requested friends, matching by owner ID rather than trusting the order of the response. A friend missing from it becomes unknown. */
    private List<DaySchedule> mineFirst(List<DaySchedule> result, List<String> friendIds) {
        Map<String, DaySchedule> byOwner = new HashMap<>();
        for (DaySchedule schedule : result) {
            byOwner.put(schedule.getOwnerId(), schedule);
        }
        String me = myId();
        List<DaySchedule> ordered = new ArrayList<>();
        DaySchedule mine = byOwner.get(me);
        ordered.add(mine != null ? mine : new DaySchedule(me, false, new ArrayList<>()));
        for (String id : friendIds) {
            DaySchedule theirs = byOwner.get(id);
            ordered.add(theirs != null ? theirs : new DaySchedule(id, false, new ArrayList<>()));
        }
        return ordered;
    }

    // the first slot is selected, like Flutter; today only slots that have not started count
    private void computeSlots() {
        slots = CommonSlotFinder.find(schedules, durationMinutes, isTodaySelected() ? nowMinute() : 0);
        selectedSlot = slots.isEmpty() ? -1 : 0;
    }

    private void setDuration(int minutes) {
        if (minutes <= 0 || minutes == durationMinutes) {
            renderDurations();
            return;
        }
        durationMinutes = minutes;
        renderDurations();
        // the busy blocks do not change with the duration, so the slots are computed again right away
        if (status == Status.READY) {
            computeSlots();
            render();
        }
    }

    private void toggleFriend(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else if (selectedIds.size() >= ScheduleRepository.MAX_FRIENDS) {
            toast("Compare up to " + ScheduleRepository.MAX_FRIENDS + " friends at a time");
            return;
        } else {
            selectedIds.add(id);
        }
        comparing = true;
        refreshDay();
    }

    // Java team: hides or shows the friends without losing the selection
    private void toggleCompare() {
        if (selectedIds.isEmpty()) {
            openFriendPicker();
            toast("Select at least one friend to compare");
            return;
        }
        comparing = !comparing;
        refreshDay();
    }

    private void selectDay(Calendar day) {
        selectedDay = day;
        refreshDay();
    }

    private void changeWeek(int offset) {
        Calendar target = (Calendar) selectedDay.clone();
        target.add(Calendar.DAY_OF_MONTH, offset * 7);
        if (target.get(Calendar.YEAR) != YEAR) {
            return;
        }
        selectDay(target);
    }

    private void importGoogleCalendar() {
        if (importStatus == Import.IMPORTING) {
            return;
        }
        importStatus = Import.IMPORTING;
        importMessage = null;
        renderImport();
        calendarImport.start();
    }

    // ---------- Render ----------

    private void render() {
        findViewById(R.id.schedule_loading).setVisibility(status == Status.LOADING ? View.VISIBLE : View.GONE);
        findViewById(R.id.schedule_error).setVisibility(status == Status.ERROR ? View.VISIBLE : View.GONE);
        findViewById(R.id.schedule_content).setVisibility(status == Status.ERROR ? View.GONE : View.VISIBLE);
        findViewById(R.id.schedule_summary).setVisibility(status == Status.ERROR ? View.GONE : View.VISIBLE);
        findViewById(R.id.schedule_fab).setVisibility(status == Status.READY ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.schedule_error_text)).setText(error == null ? "Unable to load schedule." : error);
        renderChips();
        renderImport();
        renderDurations();
        renderWeek();
        renderTimeline();
        renderBanner();
        if (refreshSlotsSheet != null) {
            refreshSlotsSheet.run();
        }
    }

    private void renderChips() {
        chips.removeAllViews();
        TextView me = chipText(getString(R.string.me_chip), 11, Color.WHITE);
        me.setBackgroundResource(R.drawable.bg_sch_me_chip);
        me.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_check, 14, Color.WHITE), null, null, null);
        me.setPadding(dp(10), dp(7), dp(10), dp(7));
        chips.addView(me, chipParams());

        for (String id : selectedIds) {
            ScheduleFriend friend = friend(id);
            if (friend != null) {
                chips.addView(friendChip(friend), chipParams());
            }
        }

        TextView add = chipText(getString(R.string.add_friends).replace("+ ", ""), 12, getColor(R.color.brand_primary));
        add.setBackgroundResource(R.drawable.bg_sch_add_chip);
        add.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_person_add, 17, getColor(R.color.brand_primary)), null, null, null);
        add.setPadding(dp(10), dp(6), dp(12), dp(6));
        add.setOnClickListener(v -> openFriendPicker());
        chips.addView(add, chipParams());

        int count = (comparing ? selectedIds.size() : 0) + 1;
        boolean active = comparing && !selectedIds.isEmpty();
        compareButton.setText((comparing ? "Comparing (" : "Compare (") + count + ")");
        compareButton.setBackgroundResource(active ? R.drawable.bg_sch_comparing_on : R.drawable.bg_sch_comparing_off);
        int textColor = active ? Color.WHITE : Color.parseColor("#64748B");
        compareButton.setTextColor(textColor);
        compareButton.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(R.drawable.ic_compare_arrows, 17, textColor), null, null, null);

        List<String> names = new ArrayList<>();
        for (String id : selectedIds) {
            ScheduleFriend friend = friend(id);
            if (friend != null) {
                names.add(friend.getShortName());
            }
        }
        friendsHeader.setText(!comparing && !selectedIds.isEmpty() ? "Friends hidden"
                : names.isEmpty() ? getString(R.string.schedule_friends) : String.join(" & ", names));
    }

    // the Flutter friend chip: initials in a light circle, the name and an x to remove
    private View friendChip(ScheduleFriend friend) {
        LinearLayout chip = new LinearLayout(this);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_sch_friend_chip);
        chip.setPadding(dp(6), dp(4), dp(5), dp(4));
        TextView avatar = chipText(friend.getInitials(), 8, getColor(R.color.brand_primary));
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_sch_avatar_light);
        chip.addView(avatar, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView name = chipText(friend.getShortName(), 10.5f, Color.parseColor("#111827"));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.setMarginStart(dp(5));
        chip.addView(name, nameParams);
        ImageView remove = new ImageView(this);
        remove.setImageResource(R.drawable.ic_close);
        remove.setImageTintList(ColorStateList.valueOf(Color.parseColor("#64748B")));
        remove.setPadding(dp(2), dp(2), dp(2), dp(2));
        remove.setContentDescription("Remove " + friend.getShortName());
        remove.setOnClickListener(v -> toggleFriend(friend.getId()));
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(dp(17), dp(17));
        removeParams.setMarginStart(dp(2));
        chip.addView(remove, removeParams);
        return chip;
    }

    private void renderImport() {
        TextView subtitle = findViewById(R.id.schedule_import_subtitle);
        TextView button = findViewById(R.id.schedule_import_btn);
        int primary = getColor(R.color.brand_primary);
        int success = getColor(R.color.brand_success);
        switch (importStatus) {
            case IMPORTING:
                subtitle.setText(R.string.schedule_import_busy);
                subtitle.setTextColor(Color.parseColor("#64748B"));
                styleImportButton(button, R.string.schedule_importing, R.drawable.bg_sch_import_busy, R.drawable.ic_download, Color.parseColor("#64748B"));
                break;
            case IMPORTED:
                subtitle.setText(importMessage != null ? importMessage : getString(R.string.schedule_import_done));
                subtitle.setTextColor(success);
                styleImportButton(button, R.string.schedule_sync_again, R.drawable.bg_sch_import_btn, R.drawable.ic_check, Color.WHITE);
                break;
            case ERROR:
                subtitle.setText(importMessage != null ? importMessage : getString(R.string.schedule_import_error));
                subtitle.setTextColor(Color.parseColor("#EF4444"));
                styleImportButton(button, R.string.schedule_try_again, R.drawable.bg_sch_import_btn, R.drawable.ic_download, Color.WHITE);
                break;
            default:
                subtitle.setText(R.string.schedule_import_idle);
                subtitle.setTextColor(Color.parseColor("#64748B"));
                styleImportButton(button, R.string.schedule_import, R.drawable.bg_sch_import_btn, R.drawable.ic_download, Color.WHITE);
        }
        button.setEnabled(importStatus != Import.IMPORTING);
        if (importStatus == Import.IMPORTED) {
            button.setTextColor(Color.WHITE);
        }
        button.setTag(primary);
    }

    private void styleImportButton(TextView button, int label, int background, int iconRes, int color) {
        button.setText(label);
        button.setBackgroundResource(background);
        button.setTextColor(color);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes, 17, color), null, null, null);
    }

    private void renderDurations() {
        durations.removeAllViews();
        for (int minutes : QUICK_DURATIONS) {
            boolean on = minutes == durationMinutes;
            TextView chip = chipText(durationLabel(minutes), 11, on ? Color.WHITE : Color.parseColor("#64748B"));
            chip.setBackgroundResource(on ? R.drawable.bg_sch_duration_on : R.drawable.bg_sch_duration_off);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            chip.setOnClickListener(v -> setDuration(minutes));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dp(6));
            durations.addView(chip, params);
        }
        if (!hoursInput.hasFocus()) {
            hoursInput.setText(String.valueOf(durationMinutes / 60));
        }
        if (!minutesInput.hasFocus()) {
            minutesInput.setText(String.format(Locale.US, "%02d", durationMinutes % 60));
        }
    }

    // the Flutter stepper: hours 0 to 12 and minutes 0 to 59 with < and >, never 0:00
    private void setUpStepper() {
        findViewById(R.id.schedule_hours_down).setOnClickListener(v -> customDuration(hours() - 1, minutes()));
        findViewById(R.id.schedule_hours_up).setOnClickListener(v -> customDuration(hours() + 1, minutes()));
        findViewById(R.id.schedule_minutes_down).setOnClickListener(v -> {
            if (minutes() == 0) {
                if (hours() > 0) {
                    customDuration(hours() - 1, 59);
                }
            } else {
                customDuration(hours(), minutes() - 1);
            }
        });
        findViewById(R.id.schedule_minutes_up).setOnClickListener(v -> {
            if (minutes() == 59) {
                customDuration(hours() + 1, 0);
            } else {
                customDuration(hours(), minutes() + 1);
            }
        });
        for (EditText field : new EditText[]{hoursInput, minutesInput}) {
            field.setOnEditorActionListener((view, action, event) -> {
                if (action == EditorInfo.IME_ACTION_DONE) {
                    applyTypedDuration();
                    view.clearFocus();
                }
                return false;
            });
            field.setOnFocusChangeListener((view, focused) -> {
                if (!focused) {
                    applyTypedDuration();
                }
            });
        }
    }

    private void applyTypedDuration() {
        customDuration(parse(hoursInput.getText(), durationMinutes / 60), parse(minutesInput.getText(), durationMinutes % 60));
    }

    private void customDuration(int hours, int minutes) {
        hours = Math.max(0, Math.min(MAX_HOURS, hours));
        minutes = Math.max(0, Math.min(59, minutes));
        if (hours == 0 && minutes == 0) {
            renderDurations();
            return;
        }
        setDuration(hours * 60 + minutes);
    }

    private int hours() {
        return durationMinutes / 60;
    }

    private int minutes() {
        return durationMinutes % 60;
    }

    private static int parse(Editable text, int fallback) {
        try {
            return Integer.parseInt(text.toString().trim());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private void renderWeek() {
        Calendar monday = startOfWeek(selectedDay);
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);
        ((TextView) findViewById(R.id.schedule_week_label)).setText(weekRangeLabel(monday, sunday));

        Calendar previous = (Calendar) selectedDay.clone();
        previous.add(Calendar.DAY_OF_MONTH, -7);
        Calendar next = (Calendar) selectedDay.clone();
        next.add(Calendar.DAY_OF_MONTH, 7);
        enable(findViewById(R.id.schedule_prev_week), previous.get(Calendar.YEAR) == YEAR);
        enable(findViewById(R.id.schedule_next_week), next.get(Calendar.YEAR) == YEAR);

        // ParchApp keeps its Monday to Friday layout
        days.removeAllViews();
        for (int i = 0; i < 5; i++) {
            Calendar day = (Calendar) monday.clone();
            day.add(Calendar.DAY_OF_MONTH, i);
            boolean on = sameDay(day, selectedDay);
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(0, dp(6), 0, dp(6));
            tab.setBackgroundResource(on ? R.drawable.bg_sch_day_on : 0);
            TextView name = chipText(format("EEE", day).toUpperCase(Locale.US), 9, on ? Color.parseColor("#C8D6FF") : Color.parseColor("#64748B"));
            name.setLetterSpacing(0.05f);
            TextView number = chipText(format("d", day), 12, on ? Color.WHITE : Color.parseColor("#111827"));
            if (!on) {
                number.setTypeface(Typeface.DEFAULT);
            }
            // a vertical LinearLayout stretches its children, so the text itself is centered like the Flutter pill
            name.setGravity(Gravity.CENTER);
            number.setGravity(Gravity.CENTER);
            tab.addView(name);
            tab.addView(number);
            tab.setOnClickListener(v -> selectDay(day));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            params.setMarginStart(dp(2));
            params.setMarginEnd(dp(2));
            days.addView(tab, params);
        }
    }

    // ---------- Timeline ----------

    private void buildTimeColumnAndGrid() {
        FrameLayout timeColumn = findViewById(R.id.schedule_time_column);
        int height = dp(END - START);
        timeColumn.getLayoutParams().height = height;
        findViewById(R.id.schedule_lanes).getLayoutParams().height = height;
        for (int minute = START; minute <= END; minute += 60) {
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E2E8F0"));
            FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            lineParams.topMargin = dp(minute - START);
            timeColumn.addView(line, lineParams);
            if (minute < END) {
                TextView label = chipText(hourLabel(minute / 60), 9, Color.parseColor("#64748B"));
                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END);
                labelParams.topMargin = dp(minute - START + 4);
                labelParams.setMarginEnd(dp(6));
                timeColumn.addView(label, labelParams);
            }
        }
    }

    private void renderTimeline() {
        laneMe.removeAllViews();
        laneFriends.removeAllViews();
        slotLayer.removeAllViews();
        addHourLines(laneMe);
        addHourLines(laneFriends);
        List<String> warnings = new ArrayList<>();

        if (!schedules.isEmpty()) {
            DaySchedule mine = schedules.get(0);
            if (!mine.isKnown()) {
                warnings.add("you");
            }
            for (TimeBlock block : mine.getBusy()) {
                addMyBlock(block);
            }
            for (int i = 1; i < schedules.size(); i++) {
                DaySchedule theirs = schedules.get(i);
                ScheduleFriend friend = friend(theirs.getOwnerId());
                String name = friend != null ? friend.getShortName() : "Busy";
                if (!theirs.isKnown()) {
                    warnings.add(friend != null ? friend.getShortName() : "a friend");
                }
                for (TimeBlock block : theirs.getBusy()) {
                    addFriendBlock(block, name);
                }
            }
        }

        // Java team: every common slot can be tapped on the timeline; the selected one looks like the Flutter one
        for (int i = 0; i < slots.size(); i++) {
            if (i != selectedSlot) {
                addSlot(laneMe, slots.get(i), i, false, false);
                addSlot(laneFriends, slots.get(i), i, false, true);
            }
        }
        if (selectedSlot >= 0) {
            addSlot(laneMe, slots.get(selectedSlot), selectedSlot, true, false);
            addSlot(laneFriends, slots.get(selectedSlot), selectedSlot, true, true);
        }
        addNowIndicator();

        boolean saved = repository instanceof OfflineScheduleRepository && ((OfflineScheduleRepository) repository).isShowingSaved();
        if (status == Status.READY && saved) {
            showState("Offline · showing your saved schedule. Planning needs a connection.");
        } else if (status == Status.READY && !warnings.isEmpty()) {
            showState("No synced calendar for " + String.join(", ", warnings)
                    + " on this day. Unknown is not counted as free.");
        } else {
            stateMessage.setVisibility(View.GONE);
        }
    }

    private void addHourLines(FrameLayout lane) {
        for (int minute = START; minute <= END; minute += 60) {
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E8EDF5"));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            params.topMargin = dp(minute - START);
            lane.addView(line, params);
        }
    }

    private void addMyBlock(TimeBlock block) {
        FrameLayout.LayoutParams params = blockParams(block.getStartMinute(), block.getEndMinute(), 6);
        if (params == null) {
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_sch_my_block);
        box.setPadding(dp(11), dp(7), dp(7), dp(7));
        box.addView(singleLine(chipText("Busy", 10, getColor(R.color.brand_primary))));
        if (params.height >= dp(48)) {
            TextView range = chipText(TimeSlot.formatMinute(block.getStartMinute()) + " – "
                    + TimeSlot.formatMinute(block.getEndMinute()), 8.5f, Color.parseColor("#64748B"));
            range.setPadding(0, dp(2), 0, 0);
            box.addView(singleLine(range));
        }
        laneMe.addView(box, params);
    }

    private void addFriendBlock(TimeBlock block, String name) {
        FrameLayout.LayoutParams params = blockParams(block.getStartMinute(), block.getEndMinute(), 6);
        if (params == null) {
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setBackgroundResource(R.drawable.bg_sch_friend_busy);
        box.setPadding(dp(8), dp(5), dp(8), dp(5));
        View dot = new View(this);
        dot.setBackgroundResource(R.drawable.bg_sch_dot_busy);
        box.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView label = singleLine(chipText(name, 9, Color.parseColor("#64748B")));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelParams.setMarginStart(dp(5));
        box.addView(label, labelParams);
        laneFriends.addView(box, params);
    }

    private void addSlot(FrameLayout lane, TimeSlot slot, int index, boolean selected, boolean friendsLane) {
        FrameLayout.LayoutParams params = blockParams(slot.getStartMinute(), slot.getEndMinute(), 3);
        if (params == null) {
            return;
        }
        params.topMargin += dp(2);
        params.height = Math.max(dp(8), params.height - dp(4));
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundResource(selected ? R.drawable.bg_sch_slot_selected : R.drawable.bg_sch_slot_other);
        box.setContentDescription("Common free slot " + slot.formatRange());
        if (selected && !friendsLane) {
            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check_circle);
            check.setImageTintList(ColorStateList.valueOf(getColor(R.color.brand_success)));
            FrameLayout.LayoutParams checkParams = new FrameLayout.LayoutParams(dp(13), dp(13));
            checkParams.setMargins(dp(4), dp(4), 0, 0);
            box.addView(check, checkParams);
        }
        box.setOnClickListener(v -> selectSlot(index));
        lane.addView(box, params);
    }

    // Flutter clamps blocks to at least 24 px so short ones stay readable
    private FrameLayout.LayoutParams blockParams(int start, int end, int sideMarginDp) {
        int from = Math.max(START, start);
        int to = Math.min(END, end);
        if (from >= to) {
            return null;
        }
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(dp(MIN_BLOCK_DP), dp(to - from)));
        params.topMargin = dp(from - START);
        params.setMarginStart(dp(sideMarginDp));
        params.setMarginEnd(dp(sideMarginDp));
        return params;
    }

    // Java team: a red line at the current time when the day is today
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
        TextView label = chipText(TimeSlot.formatMinute(now), 9, Color.WHITE);
        label.setBackgroundResource(R.drawable.bg_schedule_now_label);
        label.setPadding(dp(5), dp(1), dp(5), dp(1));
        indicator.addView(label);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(now - START) - dp(7);
        slotLayer.addView(indicator, params);
    }

    // after loading, the timeline shows the selected slot, or now when the day is today
    private void scrollToFocus() {
        int minute = selectedSlot >= 0 ? slots.get(selectedSlot).getStartMinute()
                : isTodaySelected() ? nowMinute() : START;
        ScrollView scroll = findViewById(R.id.schedule_scroll);
        scroll.post(() -> scroll.smoothScrollTo(0, Math.max(0, dp(minute - START) - dp(80))));
    }

    private void selectSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return;
        }
        selectedSlot = index;
        renderTimeline();
        renderBanner();
        if (refreshSlotsSheet != null) {
            refreshSlotsSheet.run();
        }
    }

    private void renderBanner() {
        LinearLayout banner = findViewById(R.id.schedule_summary);
        ImageView icon = findViewById(R.id.schedule_summary_icon);
        TextView title = findViewById(R.id.schedule_summary_title);
        TextView desc = findViewById(R.id.schedule_summary_desc);
        boolean empty = slots.isEmpty();
        banner.setBackgroundResource(empty ? R.drawable.bg_sch_banner_empty : R.drawable.bg_sch_banner_slots);
        icon.setBackgroundResource(empty ? R.drawable.bg_sch_banner_icon_empty : R.drawable.bg_sch_banner_icon_slots);
        icon.setImageResource(empty ? R.drawable.ic_event_busy : R.drawable.ic_bolt);
        icon.setImageTintList(ColorStateList.valueOf(empty ? Color.parseColor("#64748B") : getColor(R.color.brand_success)));
        title.setText(empty ? getString(R.string.schedule_no_common_slots)
                : slots.size() + (slots.size() == 1 ? " common slot found" : " common slots found"));
        desc.setText(selectedSlot < 0 ? getString(R.string.schedule_try_other)
                : "Selected: " + slots.get(selectedSlot).formatRange());
        findViewById(R.id.schedule_summary_expand).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ---------- Sheets and dialogs ----------

    private void openCalendarPicker() {
        Calendar initial = (Calendar) selectedDay.clone();
        if (initial.get(Calendar.YEAR) < YEAR) {
            initial.set(YEAR, Calendar.JANUARY, 1);
        } else if (initial.get(Calendar.YEAR) > YEAR) {
            initial.set(YEAR, Calendar.DECEMBER, 31);
        }
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance(BOGOTA);
            picked.clear();
            picked.set(year, month, dayOfMonth, 12, 0);
            selectDay(picked);
        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        // like Flutter, the calendar only covers 2026
        Calendar first = Calendar.getInstance(BOGOTA);
        first.set(YEAR, Calendar.JANUARY, 1, 0, 0, 0);
        Calendar last = Calendar.getInstance(BOGOTA);
        last.set(YEAR, Calendar.DECEMBER, 31, 23, 59, 59);
        dialog.getDatePicker().setMinDate(first.getTimeInMillis());
        dialog.getDatePicker().setMaxDate(last.getTimeInMillis());
        dialog.setTitle("Select a date");
        dialog.show();
    }

    private void openFriendPicker() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = sheetRoot();
        TextView title = chipText(getString(R.string.select_friends_title), 18, Color.parseColor("#111827"));
        root.addView(title);
        TextView subtitle = chipText(getString(R.string.schedule_friends_subtitle), 12, Color.parseColor("#64748B"));
        subtitle.setTypeface(Typeface.DEFAULT);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView confirm = sheetButton("", R.drawable.bg_sch_primary_btn, R.drawable.ic_compare_arrows);
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                list.removeAllViews();
                if (friends.isEmpty()) {
                    TextView empty = chipText("Join a group to compare schedules with its members.", 12, Color.parseColor("#64748B"));
                    empty.setTypeface(Typeface.DEFAULT);
                    list.addView(empty);
                }
                for (ScheduleFriend friend : friends) {
                    list.addView(friendRow(friend, this), rowParams());
                }
                int compared = (comparing ? selectedIds.size() : 0) + 1;
                confirm.setText("View comparison (" + compared + ")");
            }
        };
        refresh.run();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight(friends.size(), 70)));
        confirm.setOnClickListener(v -> sheet.dismiss());
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        confirmParams.topMargin = dp(14);
        root.addView(confirm, confirmParams);
        sheet.setContentView(root);
        sheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        sheet.show();
    }

    // the Flutter friend row: initials, full name, "Availability shared privately" and a checkbox
    private View friendRow(ScheduleFriend friend, Runnable refresh) {
        boolean picked = selectedIds.contains(friend.getId()) && comparing;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(picked ? R.drawable.bg_sch_row_friend_on : R.drawable.bg_sch_row);
        row.setPadding(dp(14), dp(10), dp(8), dp(10));
        TextView avatar = chipText(friend.getInitials(), 11, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundResource(R.drawable.bg_sch_avatar_primary);
        row.addView(avatar, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(chipText(friend.getName(), 13, Color.parseColor("#111827")));
        TextView shared = chipText(getString(R.string.schedule_shared_privately), 10, Color.parseColor("#64748B"));
        shared.setTypeface(Typeface.DEFAULT);
        texts.addView(shared);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.setMarginStart(dp(14));
        row.addView(texts, textParams);
        CheckBox box = new CheckBox(this);
        box.setChecked(picked);
        box.setClickable(false);
        box.setButtonTintList(ColorStateList.valueOf(getColor(R.color.brand_primary)));
        row.addView(box);
        row.setOnClickListener(v -> {
            if (!comparing && selectedIds.contains(friend.getId())) {
                comparing = true;
                refreshDay();
            } else {
                toggleFriend(friend.getId());
            }
            refresh.run();
        });
        return row;
    }

    private void openSlotsSheet() {
        if (slots.isEmpty()) {
            toast("No common slots for this day");
            return;
        }
        slotsSheet = new BottomSheetDialog(this);
        LinearLayout root = sheetRoot();
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(iconView(R.drawable.ic_verified, 24, getColor(R.color.brand_success)));
        TextView title = chipText(getString(R.string.available_common_slots), 17, Color.parseColor("#111827"));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMarginStart(dp(8));
        header.addView(title, titleParams);
        root.addView(header);
        TextView day = chipText(format("EEEE, MMM d", selectedDay) + " • " + durationText(), 12, Color.parseColor("#64748B"));
        day.setTypeface(Typeface.DEFAULT);
        day.setPadding(0, dp(4), 0, dp(14));
        root.addView(day);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        refreshSlotsSheet = () -> {
            list.removeAllViews();
            for (int i = 0; i < slots.size(); i++) {
                list.addView(slotRow(i), rowParams());
            }
        };
        refreshSlotsSheet.run();
        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight(slots.size(), 74)));

        TextView plan = sheetButton(getString(R.string.plan_with_slot).replace("Slot", "slot"), R.drawable.bg_sch_plan_btn, R.drawable.ic_arrow_right);
        plan.setOnClickListener(v -> {
            slotsSheet.dismiss();
            planActivity();
        });
        LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
        planParams.topMargin = dp(14);
        root.addView(plan, planParams);
        slotsSheet.setOnDismissListener(d -> refreshSlotsSheet = null);
        slotsSheet.setContentView(root);
        slotsSheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        slotsSheet.show();
    }

    // the Flutter slot row with a radio; the first one keeps the Java "Best option" tag
    private View slotRow(int index) {
        TimeSlot slot = slots.get(index);
        boolean picked = index == selectedSlot;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(picked ? R.drawable.bg_sch_row_slot_on : R.drawable.bg_sch_row_slot_off);
        row.setPadding(dp(4), dp(12), dp(12), dp(12));
        RadioButton radio = new RadioButton(this);
        radio.setChecked(picked);
        radio.setClickable(false);
        radio.setButtonTintList(ColorStateList.valueOf(getColor(R.color.brand_success)));
        row.addView(radio);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(chipText(slot.formatRange() + (index == 0 ? "   •  Best option" : ""), 13, Color.parseColor("#111827")));
        String detail = slot.isEveryoneFree()
                ? slot.getDurationMinutes() + " minutes available for everyone"
                : slot.getFreeCount() + " of " + slot.getGroupSize() + " schedules free (not synced is not counted)";
        TextView sub = chipText(detail, 10, Color.parseColor("#64748B"));
        sub.setTypeface(Typeface.DEFAULT);
        sub.setPadding(0, dp(2), 0, 0);
        texts.addView(sub);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setOnClickListener(v -> selectSlot(index));
        return row;
    }

    // Flutter preparePlan: the backend validates the slot first, then New Activity opens with it
    private void planActivity() {
        if (planning) {
            return;
        }
        if (selectedSlot < 0) {
            // Java team: without a slot you can still plan on this day, choosing the time yourself
            new CreateActivitySheet().withSuggestedDate(dateKey(selectedDay))
                    .withEntryPoint(com.example.parchapp.analytics.AnalyticsEvents.ENTRY_SCHEDULE)
                    .show(getSupportFragmentManager(), "create_activity");
            return;
        }
        TimeSlot slot = slots.get(selectedSlot);
        int version = requestVersion;
        setPlanning(true);
        List<String> participants = comparing ? new ArrayList<>(selectedIds) : new ArrayList<>();
        repository.validatePlanningSlot(participants, utcIso(selectedDay, slot.getStartMinute()),
                utcIso(selectedDay, slot.getEndMinute()), new Callback<String>() {
                    @Override
                    public void onSuccess(String comparisonId) {
                        setPlanning(false);
                        if (isFinishing() || version != requestVersion || selectedSlot < 0
                                || slots.get(selectedSlot) != slot) {
                            snackbar("The comparison changed. Select a slot again.");
                            return;
                        }
                        CreateActivitySheet.newInstance(null, schedules.size(), slot.formatRange(), -1)
                                .withEntryPoint(com.example.parchapp.analytics.AnalyticsEvents.ENTRY_SCHEDULE)
                                .withSuggestedDate(dateKey(selectedDay))
                                .withSuggestedDuration(slot.getDurationMinutes())
                                .show(getSupportFragmentManager(), "create_activity");
                    }

                    @Override
                    public void onError(Exception failure) {
                        setPlanning(false);
                        if (isFinishing()) {
                            return;
                        }
                        Log.w(TAG, "Validating the slot failed", failure);
                        snackbar(validationMessage(failure));
                    }
                });
    }

    private static String validationMessage(Exception failure) {
        if (failure instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) failure).getCode();
            if (Arrays.asList(FirebaseFunctionsException.Code.FAILED_PRECONDITION,
                    FirebaseFunctionsException.Code.PERMISSION_DENIED,
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT).contains(code)) {
                return failure.getMessage() != null ? failure.getMessage() : "Compare this slot again.";
            }
        }
        return "Unable to validate this slot. Check your connection and compare again.";
    }

    private void setPlanning(boolean value) {
        planning = value;
        View fab = findViewById(R.id.schedule_fab);
        fab.setEnabled(!value);
        fab.setAlpha(value ? 0.6f : 1f);
    }

    // ---------- Small view helpers ----------

    private LinearLayout sheetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(20));
        root.setBackgroundColor(Color.WHITE);
        View handle = new View(this);
        handle.setBackgroundResource(R.drawable.bg_home_indicator);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(dp(32), dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = dp(14);
        root.addView(handle, handleParams);
        root.setMinimumHeight(dp(200));
        return root;
    }

    private TextView sheetButton(String label, int background, int iconRes) {
        TextView button = chipText(label, 14, Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackgroundResource(background);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon(iconRes, 18, Color.WHITE), null, null, null);
        button.setPadding(dp(16), 0, dp(16), 0);
        return button;
    }

    // a long list scrolls inside the sheet so its button below always stays visible
    private int listHeight(int rows, int rowDp) {
        int wanted = Math.max(1, rows) * dp(rowDp);
        int max = Math.round(getResources().getDisplayMetrics().heightPixels * 0.5f);
        return Math.min(wanted, max);
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(dp(6));
        return params;
    }

    private TextView chipText(String value, float sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setCompoundDrawablePadding(dp(4));
        return view;
    }

    private static TextView singleLine(TextView view) {
        view.setSingleLine(true);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private android.graphics.drawable.Drawable icon(int res, int sizeDp, int color) {
        android.graphics.drawable.Drawable drawable = androidx.core.content.ContextCompat.getDrawable(this, res);
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return drawable;
    }

    private ImageView iconView(int res, int sizeDp, int color) {
        ImageView view = new ImageView(this);
        view.setImageResource(res);
        view.setImageTintList(ColorStateList.valueOf(color));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return view;
    }

    private static void enable(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.35f);
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

    private static String myId() {
        UserProfile current = UserSession.getCurrentUser();
        return current == null ? "" : current.getId();
    }

    private static String durationLabel(int minutes) {
        if (minutes == 60) {
            return "1h";
        }
        if (minutes == 120) {
            return "2h";
        }
        return minutes + "m";
    }

    private String durationText() {
        return durationMinutes % 60 == 0 ? (durationMinutes / 60) + " h"
                : durationMinutes > 60 ? (durationMinutes / 60) + " h " + (durationMinutes % 60) + " min"
                : durationMinutes + " min";
    }

    private static String hourLabel(int hour) {
        int shown = hour % 12 == 0 ? 12 : hour % 12;
        return shown + (hour < 12 ? " AM" : " PM");
    }

    private static String weekRangeLabel(Calendar monday, Calendar sunday) {
        if (monday.get(Calendar.YEAR) != sunday.get(Calendar.YEAR)) {
            return format("MMM d, yyyy", monday) + " – " + format("MMM d, yyyy", sunday);
        }
        if (monday.get(Calendar.MONTH) == sunday.get(Calendar.MONTH)) {
            return format("MMM d", monday) + "–" + format("d, yyyy", sunday);
        }
        return format("MMM d", monday) + " – " + format("MMM d, yyyy", sunday);
    }

    private static Calendar startOfWeek(Calendar day) {
        Calendar monday = (Calendar) day.clone();
        int offset = (monday.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday 0 ... Sunday 6
        monday.add(Calendar.DAY_OF_MONTH, -offset);
        return monday;
    }

    private static boolean sameDay(Calendar first, Calendar second) {
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR)
                && first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR);
    }

    private static Calendar todayInBogota() {
        Calendar today = Calendar.getInstance(BOGOTA);
        today.set(Calendar.HOUR_OF_DAY, 12);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        return today;
    }

    private boolean isTodaySelected() {
        return sameDay(selectedDay, Calendar.getInstance(BOGOTA));
    }

    private static int nowMinute() {
        Calendar now = Calendar.getInstance(BOGOTA);
        return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
    }

    private static String dateKey(Calendar day) {
        return format("yyyy-MM-dd", day);
    }

    // minute of a Bogota day as the UTC ISO string the backend expects, at minute precision
    private static String utcIso(Calendar day, int minuteOfDay) {
        Calendar time = Calendar.getInstance(BOGOTA);
        time.clear();
        time.set(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH),
                minuteOfDay / 60, minuteOfDay % 60, 0);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(time.getTime());
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

    private void snackbar(String message) {
        Snackbar.make(findViewById(R.id.schedule_content), message, Snackbar.LENGTH_LONG).show();
    }
}
