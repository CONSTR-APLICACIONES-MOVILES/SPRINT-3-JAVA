package com.example.parchapp;

import android.content.Context;
import android.widget.Toast;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.functions.FirebaseFunctionsException;

/* Eventual connectivity for actions that need the server (create, respond, change a reminder, import).
 * Before the action: offline, we do not try it and we say why, so nothing waits forever or fails in silence.
 * After the action: a lost connection gets the same honest message instead of a generic "could not ...".
 * Nothing is disabled for good: the check runs on every tap, so it works again as soon as the network returns. */
final class OfflineGuard {

    private OfflineGuard() {
    }

    /* true (and a message) when the action can not run now; action reads like "create the activity" */
    static boolean blockIfOffline(Context context, String action) {
        return blockIfOffline(context, action, null);
    }

    /* note adds what the user keeps, for example "Your form stays as you left it." */
    static boolean blockIfOffline(Context context, String action, String note) {
        if (AnalyticsTracker.getInstance().isOnline()) {
            return false;
        }
        String message = offlineMessage(action);
        Toast.makeText(context, note == null ? message : message + " " + note, Toast.LENGTH_LONG).show();
        return true;
    }

    static String offlineMessage(String action) {
        return "You're offline. Connect to the internet to " + action + ".";
    }

    /* the error came from losing the connection, not from the data or the server rules */
    static boolean isNetworkError(Exception error) {
        if (error instanceof FirebaseNetworkException) {
            return true;
        }
        if (error instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) error).getCode();
            return code == FirebaseFunctionsException.Code.UNAVAILABLE
                    || code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED;
        }
        if (error instanceof FirebaseFirestoreException) {
            return ((FirebaseFirestoreException) error).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE;
        }
        return error != null && error.getCause() instanceof Exception && error.getCause() != error
                && isNetworkError((Exception) error.getCause());
    }

    /* the message for a failed action: honest when it was the connection, the screen's own text otherwise */
    static String failureMessage(Exception error, String action, String otherwise) {
        return isNetworkError(error) || !AnalyticsTracker.getInstance().isOnline() ? offlineMessage(action) : otherwise;
    }
}
