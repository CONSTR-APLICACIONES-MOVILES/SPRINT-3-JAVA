package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class GroupDetailsSheet extends BottomSheetDialogFragment {

    private static final String ARG_ICON = "arg_icon";
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_SUBTITLE = "arg_subtitle";
    private static final String ARG_STATUS = "arg_status";

    public static GroupDetailsSheet newInstance(String icon, String title, String subtitle, String status) {
        GroupDetailsSheet sheet = new GroupDetailsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ICON, icon);
        args.putString(ARG_TITLE, title);
        args.putString(ARG_SUBTITLE, subtitle);
        args.putString(ARG_STATUS, status);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_group_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            ((TextView) view.findViewById(R.id.group_modal_icon)).setText(args.getString(ARG_ICON));
            ((TextView) view.findViewById(R.id.group_modal_title)).setText(args.getString(ARG_TITLE));
            ((TextView) view.findViewById(R.id.group_modal_subtitle)).setText(args.getString(ARG_SUBTITLE));
            ((TextView) view.findViewById(R.id.group_modal_status)).setText(args.getString(ARG_STATUS));
        }

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_ping_availability).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Availability ping sent", Toast.LENGTH_SHORT).show();
            dismiss();
        });
        view.findViewById(R.id.btn_open_chat).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Opening chat", Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }
}
