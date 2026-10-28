package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class QuickStatusSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onStatusSaved(String freeUntil, String nextActivity);
    }

    private Listener listener;
    private TextView selectedTimeChip;
    private TextView selectedActivityChip;

    public static QuickStatusSheet newInstance(Listener listener) {
        QuickStatusSheet sheet = new QuickStatusSheet();
        sheet.listener = listener;
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_quick_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText freeUntilInput = view.findViewById(R.id.input_free_until);
        EditText nextActivityInput = view.findViewById(R.id.input_next_activity);

        TextView[] timeChips = {
                view.findViewById(R.id.chip_time_3),
                view.findViewById(R.id.chip_time_4),
                view.findViewById(R.id.chip_time_5),
                view.findViewById(R.id.chip_time_6)
        };
        selectedTimeChip = view.findViewById(R.id.chip_time_4);
        for (TextView chip : timeChips) {
            chip.setOnClickListener(v -> {
                selectChip(selectedTimeChip, chip);
                selectedTimeChip = chip;
                freeUntilInput.setText(chip.getText());
            });
        }

        TextView[] activityChips = {
                view.findViewById(R.id.chip_activity_library),
                view.findViewById(R.id.chip_activity_linear),
                view.findViewById(R.id.chip_activity_gym)
        };
        selectedActivityChip = view.findViewById(R.id.chip_activity_library);
        for (TextView chip : activityChips) {
            chip.setOnClickListener(v -> {
                selectChip(selectedActivityChip, chip);
                selectedActivityChip = chip;
                nextActivityInput.setText(chip.getText());
            });
        }

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());

        Button cancelButton = view.findViewById(R.id.btn_cancel_status);
        cancelButton.setOnClickListener(v -> dismiss());

        Button saveButton = view.findViewById(R.id.btn_save_status);
        saveButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onStatusSaved(
                        freeUntilInput.getText().toString().trim(),
                        nextActivityInput.getText().toString().trim());
            }
            dismiss();
        });
    }

    private void selectChip(TextView previous, TextView next) {
        if (previous != null) {
            previous.setBackgroundResource(R.drawable.bg_chip_unselected);
            previous.setTextColor(requireContext().getColor(R.color.brand_dark));
        }
        next.setBackgroundResource(R.drawable.bg_chip_selected);
        next.setTextColor(requireContext().getColor(R.color.brand_primary));
    }
}
