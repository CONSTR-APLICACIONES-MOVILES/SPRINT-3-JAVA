package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Presentation shell for the Sprint 3 availability view; recommendation logic is implemented on its feature branch. */
public class CompareAvailabilitySheet extends BottomSheetDialogFragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_compare_availability, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.best_slot_card).setVisibility(View.GONE);
        view.findViewById(R.id.alternative_slots).setVisibility(View.GONE);
        view.findViewById(R.id.btn_schedule_in_slot).setEnabled(false);
        view.findViewById(R.id.chip_rank_overlap).setEnabled(false);
        view.findViewById(R.id.chip_rank_preference).setEnabled(false);
        view.findViewById(R.id.chip_rank_soonest).setEnabled(false);
        ((TextView) view.findViewById(R.id.slots_status_text))
                .setText("Availability recommendations are not included in this base branch.");
    }
}
