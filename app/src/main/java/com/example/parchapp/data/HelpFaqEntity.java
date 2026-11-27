package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Fts4;

// local storage: the help questions in a full-text search table, so search works offline and is indexed
// no primary key: Room uses the hidden rowid of the FTS table
@Fts4(notIndexed = {"questionId", "screen", "position"})
@Entity(tableName = "help_faq")
public class HelpFaqEntity {
    @NonNull public String questionId = "";
    @NonNull public String screen = "";
    @NonNull public String question = "";
    @NonNull public String answer = "";
    public int position;
}
