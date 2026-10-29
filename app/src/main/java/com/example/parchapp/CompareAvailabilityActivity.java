package com.example.parchapp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.List;

/* F4: full screen Compare Availability. Same engine and ranking as the sheet (CompareAvailabilityController), plus what fits better on a whole screen: switching between all my groups and seeing every common slot, not only the first three. */
public class CompareAvailabilityActivity extends AppCompatActivity implements CompareAvailabilityController.Host {

    public static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String SOURCE = "compare_screen";
    // the whole screen has room for every slot of the day
    private static final int MAX_ALTERNATIVES = 20;

    private CompareAvailabilityController controller;
    private final List<Group> groups = new ArrayList<>();
    private String selectedGroupId;

    /* groupId may be null: the screen then compares the first group of the user */
    public static Intent intent(Context context, @Nullable String groupId) {
        return new Intent(context, CompareAvailabilityActivity.class).putExtra(EXTRA_GROUP_ID, groupId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compare_availability);
        findViewById(R.id.compare_back_button).setOnClickListener(v -> finish());
        controller = new CompareAvailabilityController(findViewById(android.R.id.content), this, SOURCE, MAX_ALTERNATIVES);
// when the phone turns we keep the ranking and the slot the user picked
        controller.restoreState(savedInstanceState);
        selectedGroupId = savedInstanceState != null
                ? savedInstanceState.getString(EXTRA_GROUP_ID)
                : getIntent().getStringExtra(EXTRA_GROUP_ID);
        loadGroups();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(EXTRA_GROUP_ID, selectedGroupId);
        controller.saveState(outState);
    }

    // the chips come from GroupRepository, the same groups the dashboard shows
    private void loadGroups() {
        UserProfile user = UserSession.getCurrentUser();
        if (user == null) {
            onGroupError();
            return;
        }
        Repositories.groups().getGroups(user.getId(), new Callback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> result) {
                if (!isAlive()) {
                    return;
                }
                groups.clear();
                groups.addAll(result);
                if (groups.isEmpty()) {
                    ((TextView) findViewById(R.id.compare_group_name)).setText(R.string.no_groups_yet);
                    ((TextView) findViewById(R.id.slots_status_text)).setText(R.string.no_groups_to_compare);
                    return;
                }
                if (groupById(selectedGroupId) == null) {
                    selectedGroupId = groups.get(0).getId();
                }
                renderGroupChips();
                controller.loadGroup(selectedGroupId);
            }

            @Override
            public void onError(Exception error) {
                if (isAlive()) {
                    onGroupError();
                }
            }
        });
    }

    private void renderGroupChips() {
        LinearLayout chips = findViewById(R.id.compare_group_chips);
        chips.removeAllViews();
        for (Group group : groups) {
            boolean selected = group.getId().equals(selectedGroupId);
            TextView chip = new TextView(this);
// only the name of the group, without the emoji
            chip.setText(group.getName());
            chip.setTextSize(12);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setTextColor(getColor(selected ? R.color.brand_primary : R.color.brand_dark));
            chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
            chip.setGravity(android.view.Gravity.CENTER);
            int horizontal = dp(14);
            chip.setPadding(horizontal, 0, horizontal, 0);
            chip.setSelected(selected);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            params.setMarginEnd(dp(8));
            chip.setOnClickListener(v -> selectGroup(group.getId()));
            chips.addView(chip, params);
        }
    }

    private void selectGroup(String groupId) {
        if (groupId.equals(selectedGroupId)) {
            return;
        }
        selectedGroupId = groupId;
        renderGroupChips();
        controller.loadGroup(groupId);
    }

    private Group groupById(@Nullable String id) {
        for (Group group : groups) {
            if (group.getId().equals(id)) {
                return group;
            }
        }
        return null;
    }

    @Override
    public boolean isAlive() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void onGroupError() {
        Toast.makeText(this, "Could not load your groups", Toast.LENGTH_SHORT).show();
    }

    // the screen stays open under New Activity, so closing the form comes back to the slots
    @Override
    public void onSlotChosen(Group group, TimeSlot slot, int position, List<TimeSlot> shown) {
        CreateActivitySheet.newInstance(group.getId(), group.getSize(), slot.formatRange(), position)
                .withShownSlots(shown)
                .show(getSupportFragmentManager(), "create_activity");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
