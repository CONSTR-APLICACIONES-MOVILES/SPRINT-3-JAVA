package com.example.parchapp;

import com.example.parchapp.data.HelpFaqEntity;

import java.util.List;

// the questions of one screen of the app, as the help shows them
final class HelpSection {
    final String screen;
    final String title;
    final List<HelpFaqEntity> questions;
    final boolean origin; // true for the screen the user came from

    HelpSection(String screen, String title, List<HelpFaqEntity> questions, boolean origin) {
        this.screen = screen;
        this.title = title;
        this.questions = questions;
        this.origin = origin;
    }
}
