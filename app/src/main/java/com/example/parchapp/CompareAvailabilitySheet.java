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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/* F4: quick Compare Availability over another screen: the best slot in the green card and the next two below it. The logic lives in CompareAvailabilityController, shared with the full screen. */
public class CompareAvailabilitySheet extends BottomSheetDialogFragment implements CompareAvailabilityController.Host {

    private static final String ARG_GROUP_ID = "arg_group_id";
    private static final String SOURCE = "compare_sheet";
    private static final int MAX_ALTERNATIVES = 2;

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
        new CompareAvailabilityController(view, this, SOURCE, MAX_ALTERNATIVES)
                .loadGroup(args == null ? null : args.getString(ARG_GROUP_ID));
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
    public void onSlotChosen(Group group, TimeSlot slot, int position) {
        CreateActivitySheet.newInstance(group.getId(), group.getSize(), slot.formatRange(), position)
                .show(getParentFragmentManager(), "create_activity");
        dismiss();
    }
}
