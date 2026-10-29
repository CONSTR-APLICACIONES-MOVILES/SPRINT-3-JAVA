package com.example.parchapp;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.InsightsRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.availability.AvailabilityEngine;
import com.example.parchapp.domain.availability.OverlapRanking;
import com.example.parchapp.domain.availability.PreferenceRanking;
import com.example.parchapp.domain.availability.SlotRanker;
import com.example.parchapp.domain.availability.SoonestRanking;
import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.SlotAcceptanceInsight;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/* F4 and F2: the logic of Compare Availability, shared by the bottom sheet and the full screen. It reads the group from GroupRepository, ranks the common slots with the chosen strategy (AvailabilityEngine) and marks the BQ3 recommended position. Both hosts use the same view ids. */
final class CompareAvailabilityController {

    interface Host {
        /* false once the sheet or the screen is gone, so late answers are ignored */
        boolean isAlive();

        void onGroupError();

        /* the organiser picked a slot: open New Activity for it */
// bq3: shown are the slots the organizer saw, in the same order, so the backend measures that list
        void onSlotChosen(Group group, TimeSlot slot, int position, List<TimeSlot> shown);
    }

    private final Context context;
    private final View root;
    private final Host host;
    private final String source;
    private final int maxAlternatives;
    private final AvailabilityEngine engine = new AvailabilityEngine();

    private Group group;
    private List<TimeSlot> rankedSlots = new ArrayList<>();
    private int selectedPosition;
// keys to keep the ranking and the picked slot when the phone turns
    private static final String STATE_RANKER = "compare_ranker";
    private static final String STATE_POSITION = "compare_position";
// slot to pick again when the slots come back after the phone turns, -1 when there is none
    private int restoredPosition = -1;
    // f2: which position gets the Recommended badge, it comes from the BQ3 result in firestore
    private int recommendedPosition = InsightsRepository.DEFAULT_RECOMMENDED_POSITION;

    private final TextView statusText;
// bq3: the answer of the question, shown on top of the slots
    private final TextView bq3InsightText;
    private final View bestSlotCard;
    private final LinearLayout alternativeSlots;
    private final Button scheduleButton;
    private final TextView overlapChip;
    private final TextView preferenceChip;
    private final TextView soonestChip;

    /* source is logged with BQ1; maxAlternatives is how many slots go under the best one */
    CompareAvailabilityController(View root, Host host, String source, int maxAlternatives) {
        this.context = root.getContext();
        this.root = root;
        this.host = host;
        this.source = source;
        this.maxAlternatives = maxAlternatives;
        // bq8: opening the sheet or the full screen is one use of compare availability, source says which one
        AnalyticsTracker.getInstance().trackFeature(AnalyticsEvents.FEATURE_COMPARE_AVAILABILITY, source);

        statusText = root.findViewById(R.id.slots_status_text);
        bq3InsightText = root.findViewById(R.id.bq3_insight_text);
        bestSlotCard = root.findViewById(R.id.best_slot_card);
        alternativeSlots = root.findViewById(R.id.alternative_slots);
        scheduleButton = root.findViewById(R.id.btn_schedule_in_slot);
        bestSlotCard.setOnClickListener(v -> selectSlot(0));
        scheduleButton.setOnClickListener(v -> scheduleSelectedSlot());
        scheduleButton.setEnabled(false);

        // Overlap is the default; each chip applies its strategy to the same common slots.
        overlapChip = root.findViewById(R.id.chip_rank_overlap);
        preferenceChip = root.findViewById(R.id.chip_rank_preference);
        soonestChip = root.findViewById(R.id.chip_rank_soonest);
        overlapChip.setOnClickListener(v -> useRanker(new OverlapRanking()));
        preferenceChip.setOnClickListener(v -> useRanker(new PreferenceRanking()));
        soonestChip.setOnClickListener(v -> useRanker(new SoonestRanking()));

// bq3: one read gives the recommended position for the badge and the text with the percent
        Repositories.insights().getSlotAcceptance(new Callback<SlotAcceptanceInsight>() {
            @Override
            public void onSuccess(SlotAcceptanceInsight insight) {
                if (!host.isAlive()) {
                    return;
                }
                recommendedPosition = insight.getRecommendedPosition();
                showSlotAcceptance(insight);
                // the slots can arrive before or after this answer, so we redraw if they are already there
                if (host.isAlive() && !rankedSlots.isEmpty()) {
                    renderSlots();
                }
            }

            @Override
            public void onError(Exception error) {
            }
        });
    }

// bq3: with enough slots it says the percent and the best position, if not it says why the first slot is recommended
    private void showSlotAcceptance(SlotAcceptanceInsight insight) {
        int position = insight.getRecommendedPosition() + 1;
        if (!insight.hasEnoughData()) {
            bq3InsightText.setText(context.getString(R.string.bq3_insight_no_data, SlotAcceptanceInsight.MIN_SAMPLE));
        } else if (insight.noneAcceptedUnchanged()) {
// with 0 percent there is no best position, so we do not say that one works best
            bq3InsightText.setText(context.getString(R.string.bq3_insight_all_changed, insight.getSampleSize()));
        } else {
            bq3InsightText.setText(context.getString(R.string.bq3_insight,
                    insight.roundedPercent(), position, insight.getSampleSize()));
        }
        bq3InsightText.setVisibility(View.VISIBLE);
    }

    void loadGroup(String groupId) {
        if (groupId == null) {
            showGroupError();
            return;
        }
        group = null;
        rankedSlots = new ArrayList<>();
        alternativeSlots.removeAllViews();
        bestSlotCard.setVisibility(View.GONE);
        statusText.setText(R.string.calculating_slots);
        statusText.setVisibility(View.VISIBLE);
        scheduleButton.setEnabled(false);
        Repositories.groups().getGroup(groupId, new Callback<Group>() {
            @Override
            public void onSuccess(Group result) {
                if (!host.isAlive()) {
                    return;
                }
                group = result;
                ((TextView) root.findViewById(R.id.compare_group_name)).setText(String.format(Locale.US,
                        "%s • %d member schedules", group.getName(), group.getSize()));
                calculateSlots();
            }

            @Override
            public void onError(Exception error) {
                if (host.isAlive()) {
                    showGroupError();
                }
            }
        });
    }

    private void showGroupError() {
        statusText.setText(R.string.no_common_slots);
        statusText.setVisibility(View.VISIBLE);
        host.onGroupError();
    }

// the host calls this before android makes the screen again
    void saveState(Bundle outState) {
        outState.putString(STATE_RANKER, engine.getRanker().getName());
        outState.putInt(STATE_POSITION, selectedPosition);
    }

// the host calls this after making the controller again, before the group loads
    void restoreState(Bundle savedState) {
        if (savedState == null) {
            return;
        }
        String ranker = savedState.getString(STATE_RANKER);
        if ("preference".equals(ranker)) {
            useRanker(new PreferenceRanking());
        } else if ("soonest".equals(ranker)) {
            useRanker(new SoonestRanking());
        }
        restoredPosition = savedState.getInt(STATE_POSITION, -1);
    }

    private void useRanker(SlotRanker ranker) {
        if (ranker.getName().equals(engine.getRanker().getName())) {
            return;
        }
        engine.setRanker(ranker);
        styleRankerChip(overlapChip, ranker.getName().equals("overlap"));
        styleRankerChip(preferenceChip, ranker.getName().equals("preference"));
        styleRankerChip(soonestChip, ranker.getName().equals("soonest"));
        calculateSlots();
    }

    private void styleRankerChip(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        chip.setTextColor(context.getColor(selected ? R.color.brand_primary : R.color.brand_dark));
    }

    // the engine runs in a background thread and answers on the main one, so the screen never freezes
    private void calculateSlots() {
        if (group == null) {
            return;
        }
        Group asked = group;
        statusText.setText(R.string.calculating_slots);
        statusText.setVisibility(View.VISIBLE);
        scheduleButton.setEnabled(false);
        engine.findCommonSlotsAsync(group.getMembers(), source, new Callback<List<TimeSlot>>() {
            @Override
            public void onSuccess(List<TimeSlot> slots) {
                // the user may have switched group while this was running
                if (!host.isAlive() || asked != group) {
                    return;
                }
                rankedSlots = slots;
// after the phone turns we pick again the slot the user had, if it is still on the screen
                boolean restore = restoredPosition >= 0 && restoredPosition < slots.size()
                        && restoredPosition <= maxAlternatives;
                selectedPosition = restore ? restoredPosition : 0;
                restoredPosition = -1;
                renderSlots();
            }

            @Override
            public void onError(Exception error) {
                if (host.isAlive()) {
                    statusText.setText(R.string.no_common_slots);
                }
            }
        });
    }

    private void renderSlots() {
        alternativeSlots.removeAllViews();
        if (rankedSlots.isEmpty()) {
            statusText.setText(R.string.no_common_slots);
            statusText.setVisibility(View.VISIBLE);
            bestSlotCard.setVisibility(View.GONE);
            scheduleButton.setEnabled(false);
            return;
        }
        statusText.setVisibility(View.GONE);
        bestSlotCard.setVisibility(View.VISIBLE);

        TimeSlot best = rankedSlots.get(0);
        ((TextView) root.findViewById(R.id.best_slot_label)).setText(bestSlotLabel());
        ((TextView) root.findViewById(R.id.best_slot_badge)).setText(String.format(Locale.US,
                "%d/%d Free", best.getFreeCount(), best.getGroupSize()));
        ((TextView) root.findViewById(R.id.best_slot_time)).setText(best.formatRange());
        ((TextView) root.findViewById(R.id.best_slot_note)).setText(best.isEveryoneFree()
                ? "Everyone in the group is free."
                : "Free: " + freeMemberNames(best));
        bestSlotCard.setAlpha(selectedPosition == 0 ? 1f : 0.55f);

        int alternatives = Math.min(maxAlternatives, rankedSlots.size() - 1);
        for (int position = 1; position <= alternatives; position++) {
            alternativeSlots.addView(alternativeRow(position));
        }

        scheduleButton.setText("⚡  Schedule Plan • " + rankedSlots.get(selectedPosition).formatRange());
        scheduleButton.setEnabled(true);
    }

    private String bestSlotLabel() {
        String label = context.getString(R.string.best_mutual_match);
        return recommendedPosition == 0 ? label + "  •  ★ " + context.getString(R.string.recommended) : label;
    }

    private String alternativeText(int position) {
        TimeSlot slot = rankedSlots.get(position);
        String text = String.format(Locale.US, "%s   •   %d/%d free", slot.formatRange(),
                slot.getFreeCount(), slot.getGroupSize());
        return position == recommendedPosition ? text + "   •   ★ " + context.getString(R.string.recommended) : text;
    }

    private TextView alternativeRow(int position) {
        boolean selected = position == selectedPosition;
        TextView row = new TextView(context);
        row.setText(alternativeText(position));
        row.setTextSize(12);
        row.setTextColor(context.getColor(selected ? R.color.brand_primary : R.color.brand_dark));
        row.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip_unselected);
        int padding = dp(12);
        row.setPadding(padding, padding, padding, padding);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(8);
        row.setLayoutParams(params);
        row.setOnClickListener(v -> selectSlot(position));
        return row;
    }

    private void selectSlot(int position) {
        if (position >= rankedSlots.size()) {
            return;
        }
        selectedPosition = position;
        renderSlots();
    }

    private void scheduleSelectedSlot() {
        if (rankedSlots.isEmpty() || group == null) {
            return;
        }
        TimeSlot slot = rankedSlots.get(selectedPosition);
// f2: we save which suggested slot the organizer picked and the group size
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_SLOT_POSITION, selectedPosition);
        params.put(AnalyticsEvents.PARAM_GROUP_SIZE, group.getSize());
        AnalyticsTracker.getInstance().track(AnalyticsEvents.SLOT_SELECTED, params);
        host.onSlotChosen(group, slot, selectedPosition, shownSlots());
    }

// the best slot and the alternatives that are on the screen, the same ones renderSlots draws
    private List<TimeSlot> shownSlots() {
        return new ArrayList<>(rankedSlots.subList(0, Math.min(rankedSlots.size(), maxAlternatives + 1)));
    }

    private String freeMemberNames(TimeSlot slot) {
        StringBuilder names = new StringBuilder();
        for (Member member : group.getMembers()) {
            if (slot.getFreeMemberIds().contains(member.getId())) {
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(member.getName());
            }
        }
        return names.toString();
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
