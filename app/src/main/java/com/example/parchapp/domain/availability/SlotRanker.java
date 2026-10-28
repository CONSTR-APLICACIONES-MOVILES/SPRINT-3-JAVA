package com.example.parchapp.domain.availability;

import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeSlot;

import java.util.List;

/* Strategy used by {@link AvailabilityEngine} to order common slots. A new ranking criterion (preferred hours, soonest) only needs a new implementation. */
public interface SlotRanker {
    /* Short stable identifier, logged with BQ1 events. */
    String getName();

    /* Returns in this scenario a new list with the best slot first. Must not modify {@code slots}. */
    List<TimeSlot> rank(List<TimeSlot> slots, List<Member> members);
}
