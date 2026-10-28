package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.StatusService;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class AvailabilityStatusSheet extends BottomSheetDialogFragment implements StatusService.Listener {

    private View selectedStatusRow;
    private ImageView selectedStatusCheck;
    private String selectedStatusName = "Commute / In Transit";

    private TextView selectedDurationChip;
    private String selectedDurationName = "For 1 hour";
    private StatusService statusService;
    private TextView motionDetectionMessage;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_availability_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        statusService = Repositories.status();
        motionDetectionMessage = view.findViewById(R.id.motion_detection_message);

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());

        setupStatusRow(view, R.id.status_available, R.id.check_available, "Available now");
        setupStatusRow(view, R.id.status_class, R.id.check_class, "In Class");
        setupStatusRow(view, R.id.status_focus, R.id.check_focus, "Deep Focus");
        setupStatusRow(view, R.id.status_busy, R.id.check_busy, "Busy / Personal");
        setupStatusRow(view, R.id.status_transit, R.id.check_transit, "Commute / In Transit");
        // the xml starts with commute highlighted, so we treat it as the selected row and selecting the saved status clears it
        selectedStatusRow = view.findViewById(R.id.status_transit);
        selectedStatusCheck = view.findViewById(R.id.check_transit);
        selectSavedStatus(view, statusService.getAvailability());

        TextView[] durationChips = {
                view.findViewById(R.id.duration_1h),
                view.findViewById(R.id.duration_6pm),
                view.findViewById(R.id.duration_tomorrow),
                view.findViewById(R.id.duration_manual)
        };
        String[] durationShortLabels = {"1h", "Until 6PM", "Until tomorrow", "Manual"};
        selectedDurationChip = durationChips[0];
        TextView durationSelectedLabel = view.findViewById(R.id.duration_selected_label);
        for (int i = 0; i < durationChips.length; i++) {
            TextView chip = durationChips[i];
            String shortLabel = durationShortLabels[i];
            chip.setOnClickListener(v -> {
                selectDurationChip(chip);
                durationSelectedLabel.setText("◷ " + shortLabel + " selected");
                selectedDurationName = chip.getText().toString();
            });
        }

        view.findViewById(R.id.save_status_button).setOnClickListener(v -> {
            Repositories.status().setAvailability(selectedStatusName, selectedDurationName);
            Toast.makeText(requireContext(), selectedStatusName + " · " + selectedDurationName, Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (statusService == null) return;
        statusService.addListener(this);
        boolean started = statusService.startMotionDetection();
        motionDetectionMessage.setText(!statusService.isMotionDetectionAvailable()
                ? "Movement suggestions are unavailable: this device has no accelerometer."
                : started ? "Movement detection is active while this sheet is open."
                : "Could not start movement detection on this device.");
    }

    @Override
    public void onStop() {
        if (statusService != null) {
            statusService.removeListener(this);
            statusService.stopMotionDetection();
        }
        super.onStop();
    }

    @Override
    public void onStatusChanged(StatusService status) { }

    @Override
    public void onCommuteSuggested(StatusService status) {
        if (!isAdded() || getView() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Movement detected")
                .setMessage("Would you like to set your status to Commute / In Transit?")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Set status", (dialog, which) -> {
                    selectStatus(getView(), R.id.status_transit, R.id.check_transit,
                            "Commute / In Transit");
                    selectedDurationName = "For 1 hour";
                    status.setAvailabilityFromMotion(selectedStatusName, selectedDurationName);
                    Toast.makeText(requireContext(), "Commute status saved", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void selectSavedStatus(View root, String savedStatus) {
        int rowId = R.id.status_available;
        int checkId = R.id.check_available;
        String name = "Available now";
        if ("In Class".equals(savedStatus) || "inClass".equals(savedStatus)) {
            rowId = R.id.status_class; checkId = R.id.check_class; name = "In Class";
        } else if ("Deep Focus".equals(savedStatus) || "focused".equals(savedStatus)) {
            rowId = R.id.status_focus; checkId = R.id.check_focus; name = "Deep Focus";
        } else if ("Busy / Personal".equals(savedStatus) || "busy".equals(savedStatus)) {
            rowId = R.id.status_busy; checkId = R.id.check_busy; name = "Busy / Personal";
        } else if ("Commute / In Transit".equals(savedStatus) || "inTransit".equals(savedStatus)) {
            rowId = R.id.status_transit; checkId = R.id.check_transit; name = "Commute / In Transit";
        }
        selectStatus(root, rowId, checkId, name);
    }

    private void selectStatus(View root, int rowId, int checkId, String name) {
        if (selectedStatusRow != null) {
            selectedStatusRow.setBackgroundResource(R.drawable.bg_option_row_unselected);
            selectedStatusCheck.setVisibility(View.GONE);
            tintCheckCircle(selectedStatusCheck, android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor("#F1F5F9")));
        }
        View row = root.findViewById(rowId);
        ImageView check = root.findViewById(checkId);
        row.setBackgroundResource(R.drawable.bg_option_row_selected);
        check.setVisibility(View.VISIBLE);
        tintCheckCircle(check, androidx.core.content.ContextCompat.getColorStateList(requireContext(), R.color.brand_primary));
        selectedStatusRow = row;
        selectedStatusCheck = check;
        selectedStatusName = name;
    }

    // the round badge behind the check is the first child of its frame
    private static void tintCheckCircle(ImageView check, android.content.res.ColorStateList color) {
        android.view.ViewGroup frame = (android.view.ViewGroup) check.getParent();
        frame.getChildAt(0).setBackgroundTintList(color);
    }

    private void setupStatusRow(View root, int rowId, int checkId, String statusName) {
        View row = root.findViewById(rowId);
        ImageView check = root.findViewById(checkId);
        row.setOnClickListener(v -> {
            selectStatus(root, rowId, checkId, statusName);
        });
    }

    private void selectDurationChip(TextView chip) {
        if (selectedDurationChip != null) {
            selectedDurationChip.setBackgroundResource(R.drawable.bg_chip_unselected);
            selectedDurationChip.setTextColor(requireContext().getColor(R.color.brand_dark));
        }
        chip.setBackgroundResource(R.drawable.bg_chip_selected);
        chip.setTextColor(requireContext().getColor(R.color.brand_primary));
        selectedDurationChip = chip;
    }
}
