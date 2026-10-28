package com.example.parchapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.example.parchapp.domain.model.Group;

import java.util.ArrayList;
import java.util.List;

public class AllGroupsSheet extends BottomSheetDialogFragment {

    public interface Listener {
        void onGroupRowSelected(String groupKey);
    }

    private Listener listener;
    private List<Group> groups = new ArrayList<>();

    public static AllGroupsSheet newInstance(Listener listener) {
        return newInstance(listener, new ArrayList<>());
    }

    public static AllGroupsSheet newInstance(Listener listener, List<Group> groups) {
        AllGroupsSheet sheet = new AllGroupsSheet();
        sheet.listener = listener;
        sheet.groups = new ArrayList<>(groups);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_all_groups, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.sheet_close_btn).setOnClickListener(v -> dismiss());
        int[] rows = {R.id.row_group_eng, R.id.row_group_roomies, R.id.row_group_intramurals,
                R.id.row_group_thesis, R.id.row_group_choir};
        int[] icons = {R.id.row_group_eng_icon, R.id.row_group_roomies_icon, R.id.row_group_intramurals_icon,
                R.id.row_group_thesis_icon, R.id.row_group_choir_icon};
        int[] titles = {R.id.row_group_eng_title, R.id.row_group_roomies_title, R.id.row_group_intramurals_title,
                R.id.row_group_thesis_title, R.id.row_group_choir_title};
        int[] subtitles = {R.id.row_group_eng_subtitle, R.id.row_group_roomies_subtitle,
                R.id.row_group_intramurals_subtitle, R.id.row_group_thesis_subtitle, R.id.row_group_choir_subtitle};
        for (int i = 0; i < rows.length; i++) {
            if (i >= groups.size()) {
                rootHide(view, rows[i]);
                continue;
            }
            Group group = groups.get(i);
            view.findViewById(rows[i]).setVisibility(View.VISIBLE);
            ((android.widget.TextView) view.findViewById(icons[i])).setText(group.getIcon());
            ((android.widget.TextView) view.findViewById(titles[i])).setText(group.getName());
            ((android.widget.TextView) view.findViewById(subtitles[i])).setText(group.getSize() + " members");
            setRowListener(view, rows[i], group.getId());
        }
    }

    private void setRowListener(View root, int viewId, String groupKey) {
        root.findViewById(viewId).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onGroupRowSelected(groupKey);
            }
        });
    }

    private void rootHide(View root, int viewId) {
        root.findViewById(viewId).setVisibility(View.GONE);
    }
}
