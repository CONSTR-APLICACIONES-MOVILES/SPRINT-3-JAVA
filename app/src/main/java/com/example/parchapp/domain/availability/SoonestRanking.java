package com.example.parchapp.domain.availability;

import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeSlot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orders slots by earliest start, then by free-member count and duration. */
public class SoonestRanking implements SlotRanker {
    @Override
    public String getName() {
        return "soonest";
    }

    @Override
    public List<TimeSlot> rank(List<TimeSlot> slots, List<Member> members) {
        List<TimeSlot> ranked = new ArrayList<>(slots);
        ranked.sort(Comparator.comparingInt(TimeSlot::getStartMinute)
                .thenComparing(Comparator.comparingInt(TimeSlot::getFreeCount).reversed())
                .thenComparing(Comparator.comparingInt(TimeSlot::getDurationMinutes).reversed()));
        return ranked;
    }
}
