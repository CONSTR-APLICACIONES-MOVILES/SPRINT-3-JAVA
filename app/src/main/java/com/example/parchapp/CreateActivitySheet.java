package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/** Activity creation view shell. Publishing/invitations are restored on their feature branch. */
public class CreateActivitySheet extends BottomSheetDialogFragment {
    private static final String ARG_SLOT_TIME = "arg_slot_time";
    private static final String ARG_SLOT_DATE = "arg_slot_date";

    public static CreateActivitySheet newInstance(String groupId, int groupSize, String slotTime,
                                                  int slotPosition) {
        CreateActivitySheet sheet = new CreateActivitySheet();
        Bundle args = new Bundle();
        args.putString(ARG_SLOT_TIME, slotTime);
        sheet.setArguments(args);
        return sheet;
    }

    public CreateActivitySheet withSuggestedDate(String date) {
        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        args.putString(ARG_SLOT_DATE, date);
        setArguments(args);
        return this;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_create_activity, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            String time = args.getString(ARG_SLOT_TIME);
            String date = args.getString(ARG_SLOT_DATE);
            if (time != null) ((EditText) view.findViewById(R.id.input_activity_time)).setText(time);
            if (date != null) ((EditText) view.findViewById(R.id.input_activity_date)).setText(date);
        }
        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_create_activity).setEnabled(false);
        view.findViewById(R.id.chip_group_eng).setEnabled(false);
        view.findViewById(R.id.chip_group_roomies).setEnabled(false);
    }
}
