package com.example.parchapp;

import java.util.List;

// what the help screen draws: the questions by screen and when they were last downloaded (0 = never)
final class HelpScreenState {
    final List<HelpSection> sections;
    final long lastSync;

    HelpScreenState(List<HelpSection> sections, long lastSync) {
        this.sections = sections;
        this.lastSync = lastSync;
    }
}
