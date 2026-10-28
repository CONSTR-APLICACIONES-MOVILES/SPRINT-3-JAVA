package com.example.parchapp.domain.availability;

import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// f4: this ranking puts first the slots that match better the preferred hours of the free members
// if two slots have the same score, the one with more free members goes first, then the earliest one
public class PreferenceRanking implements SlotRanker {

// f4: name that goes in the bq1 event
    @Override
    public String getName() {
        return "preference";
    }

    @Override
    public List<TimeSlot> rank(List<TimeSlot> slots, List<Member> members) {
// f4: we save the members by id so we can find them fast
        Map<String, Member> membersById = new HashMap<>();
        for (Member member : members) {
            membersById.put(member.getId(), member);
        }
// f4: we calculate the score of every slot only one time
        Map<TimeSlot, Integer> scores = new HashMap<>();
        for (TimeSlot slot : slots) {
            scores.put(slot, preferenceScore(slot, membersById));
        }

// f4: we make a new list so we do not change the list we got
        List<TimeSlot> ranked = new ArrayList<>(slots);
        ranked.sort(Comparator.<TimeSlot>comparingInt(scores::get).reversed()
                .thenComparing(Comparator.comparingInt(TimeSlot::getFreeCount).reversed())
                .thenComparingInt(TimeSlot::getStartMinute));
        return ranked;
    }

// f4: the score is the sum of the minutes of the slot that are inside the preferred hours of each free member
    private int preferenceScore(TimeSlot slot, Map<String, Member> membersById) {
        int score = 0;
        for (String memberId : slot.getFreeMemberIds()) {
            Member member = membersById.get(memberId);
            if (member == null) {
                continue;
            }
            int overlapStart = Math.max(slot.getStartMinute(), member.getPreferredStartMinute());
            int overlapEnd = Math.min(slot.getEndMinute(), member.getPreferredEndMinute());
            score += Math.max(0, overlapEnd - overlapStart);
        }
        return score;
    }
}
