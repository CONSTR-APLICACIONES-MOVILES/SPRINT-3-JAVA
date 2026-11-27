package com.example.parchapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parchapp.data.HelpFaqEntity;

import java.util.ArrayList;
import java.util.List;

// the rows of the help: a title for each screen, its questions, and "About ParchApp" at the end.
// an answer opens in place: only that row is told what changed (payload), the rest of the row is not drawn again
final class HelpAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_SECTION = 0;
    private static final int TYPE_QUESTION = 1;
    private static final int TYPE_ABOUT = 2;
    private static final Object PAYLOAD_EXPAND = new Object();

    private static final class Row {
        final int type;
        final String text;
        final HelpFaqEntity faq;
        boolean expanded;

        Row(int type, String text, HelpFaqEntity faq) {
            this.type = type;
            this.text = text;
            this.faq = faq;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final String aboutText;

    HelpAdapter(String aboutText) {
        this.aboutText = aboutText;
    }

    // the questions grouped by screen, with a title before each group
    void setSections(List<HelpSection> sections) {
        rows.clear();
        for (HelpSection section : sections) {
            rows.add(new Row(TYPE_SECTION, section.origin ? section.title + " (this screen)" : section.title, null));
            for (HelpFaqEntity faq : section.questions) rows.add(new Row(TYPE_QUESTION, null, faq));
        }
        rows.add(new Row(TYPE_ABOUT, aboutText, null));
        notifyDataSetChanged();
    }

    // search results: only the questions, without titles and without "About"
    void setResults(List<HelpFaqEntity> results) {
        rows.clear();
        for (HelpFaqEntity faq : results) rows.add(new Row(TYPE_QUESTION, null, faq));
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).type;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (type == TYPE_QUESTION) {
            return new QuestionHolder(inflater.inflate(R.layout.item_help_question, parent, false));
        }
        int layout = type == TYPE_SECTION ? R.layout.item_help_section : R.layout.item_help_about;
        return new TextHolder(inflater.inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof QuestionHolder) {
            QuestionHolder question = (QuestionHolder) holder;
            question.question.setText(row.faq.question);
            question.answer.setText(row.faq.answer);
            question.itemView.setOnClickListener(v -> toggle(question.getBindingAdapterPosition()));
            question.showExpanded(row.expanded);
        } else {
            ((TextHolder) holder).text.setText(row.text);
        }
    }

    // payload: only the answer and the arrow change, the text of the question is not set again
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_EXPAND) && holder instanceof QuestionHolder) {
            ((QuestionHolder) holder).showExpanded(rows.get(position).expanded);
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    private void toggle(int position) {
        if (position == RecyclerView.NO_POSITION) return;
        Row row = rows.get(position);
        row.expanded = !row.expanded;
        notifyItemChanged(position, PAYLOAD_EXPAND);
    }

    private static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView text;

        TextHolder(View view) {
            super(view);
            text = view.findViewById(R.id.help_row_text);
        }
    }

    private static final class QuestionHolder extends RecyclerView.ViewHolder {
        final TextView question;
        final TextView answer;
        final View arrow;

        QuestionHolder(View view) {
            super(view);
            question = view.findViewById(R.id.help_question);
            answer = view.findViewById(R.id.help_answer);
            arrow = view.findViewById(R.id.help_arrow);
        }

        void showExpanded(boolean expanded) {
            answer.setVisibility(expanded ? View.VISIBLE : View.GONE);
            arrow.setRotation(expanded ? 90f : 0f);
        }
    }
}
