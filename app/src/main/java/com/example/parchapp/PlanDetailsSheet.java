package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.parchapp.util.ExternalActions;

public class PlanDetailsSheet extends BottomSheetDialogFragment {
    private static final String ARG_TITLE = "arg_title";
    private static final String ARG_STATUS = "arg_status";
    private static final String ARG_DATE = "arg_date";
    private static final String ARG_TIME = "arg_time";
    private static final String ARG_LOCATION = "arg_location";

    public static PlanDetailsSheet newInstance(String title, String status, String date,
                                               String time, String location) {
        PlanDetailsSheet sheet = new PlanDetailsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_STATUS, status);
        args.putString(ARG_DATE, date);
        args.putString(ARG_TIME, time);
        args.putString(ARG_LOCATION, location);
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_plan_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        String title = args == null ? "" : args.getString(ARG_TITLE, "");
        String status = args == null ? "" : args.getString(ARG_STATUS, "");
        String date = args == null ? "" : args.getString(ARG_DATE, "");
        String time = args == null ? "" : args.getString(ARG_TIME, "");
        String location = args == null ? "" : args.getString(ARG_LOCATION, "");
        ((android.widget.TextView) view.findViewById(R.id.plan_details_status)).setText(status);
        ((android.widget.TextView) view.findViewById(R.id.plan_details_title)).setText(title);
        ((android.widget.TextView) view.findViewById(R.id.plan_details_time)).setText((date + " " + time).trim());
        ((android.widget.TextView) view.findViewById(R.id.plan_details_location)).setText(location);

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_plan_close).setOnClickListener(v -> dismiss());
        view.findViewById(R.id.btn_open_map).setEnabled(false);
        view.findViewById(R.id.btn_add_to_calendar).setOnClickListener(v -> {
            if (ExternalActions.openCalendar(requireContext(), title, date, time, location)) {
                Toast.makeText(requireContext(), "Calendar event ready to save", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "No calendar application is available", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
