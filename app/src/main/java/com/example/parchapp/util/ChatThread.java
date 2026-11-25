package com.example.parchapp.util;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import java.util.concurrent.Executor;

/* Multithreading, a different model from the ExecutorService pools: a HandlerThread is one thread with its own
 * Looper, a queue of messages that it takes one by one. The chat listener of Firestore delivers every change to
 * this queue (EXECUTOR), the messages are turned into rows here, and HANDLER_MAIN posts the result to the
 * Looper of the main thread, the only one that touches the screen.
 * One thread for every chat: the changes of a conversation arrive in order and the SimpleDateFormat used to
 * build the rows is only touched here, so it needs no lock (thread confinement). */
public final class ChatThread {
    private static final HandlerThread THREAD = new HandlerThread("parch-chat", Process.THREAD_PRIORITY_BACKGROUND);
    private static final Handler HANDLER;
    private static final Handler HANDLER_MAIN = new Handler(Looper.getMainLooper());
    // what Firestore needs to call its listener on our thread: post each callback to the queue of the Looper
    public static final Executor EXECUTOR;

    static {
        THREAD.start();
        HANDLER = new Handler(THREAD.getLooper());
        EXECUTOR = HANDLER::post;
    }

    private ChatThread() {
    }

    public static void toMain(Runnable work) {
        HANDLER_MAIN.post(work);
    }

    public static boolean isCurrent() {
        return Looper.myLooper() == THREAD.getLooper();
    }
}
