package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.TimeSlot;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/* F4: quick Compare Availability over another screen: the best slot in the green card and the next two below it. The logic lives in CompareAvailabilityController, shared with the full screen. */
public class CompareAvailabilitySheet extends BottomSheetDialogFragment implements CompareAvailabilityController.Host {

    private static final String ARG_GROUP_ID = "arg_group_id";
    private static final String SOURCE = "compare_sheet";
    private static final int MAX_ALTERNATIVES = 2;
    private CompareAvailabilityController controller;

    public static CompareAvailabilitySheet newInstance(String groupId) {
        CompareAvailabilitySheet sheet = new CompareAvailabilitySheet();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_compare_availability, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        controller = new CompareAvailabilityController(view, this, SOURCE, MAX_ALTERNATIVES);
// when the phone turns we keep the ranking and the slot the user picked
        controller.restoreState(savedInstanceState);
        controller.loadGroup(args == null ? null : args.getString(ARG_GROUP_ID));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (controller != null) {
            controller.saveState(outState);
        }
    }

    @Override
    public void onDestroyView() {
        if (controller != null) {
            controller.stop();
        }
        super.onDestroyView();
    }

// the sheet opens fully, in landscape half open only showed the title
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior = ((BottomSheetDialog) getDialog()).getBehavior();
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public boolean isAlive() {
        return getView() != null;
    }

    @Override
    public void onGroupError() {
        Toast.makeText(requireContext(), "Could not load the group", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSlotChosen(Group group, TimeSlot slot, int position, List<TimeSlot> shown) {
        CreateActivitySheet.newInstance(group.getId(), group.getSize(), slot.formatRange(), position)
                .withShownSlots(shown)
                .withEntryPoint(com.example.parchapp.analytics.AnalyticsEvents.ENTRY_SLOT)
                .show(getParentFragmentManager(), "create_activity");
        dismiss();
    }
}
