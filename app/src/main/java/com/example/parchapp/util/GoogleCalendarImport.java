package com.example.parchapp.util;

import android.content.Intent;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.GoogleFreeBusyClient;
import com.example.parchapp.data.Repositories;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/* The Google Calendar import of the Flutter Schedule, in Java: Google sign-in with only the free/busy permission, the freeBusy call for the next 56 days, and importCalendarAvailability on the backend. Create it in onCreate, it registers activity result launchers. */
public final class GoogleCalendarImport {
    static final String FREE_BUSY_SCOPE = "https://www.googleapis.com/auth/calendar.freebusy";
    static final int DAYS = 56;
    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");
    // network and the token request block, so they run here, never on the main thread
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final AppCompatActivity activity;
    private final Callback<Integer> onDone;
    private final ActivityResultLauncher<Intent> signIn;
    private final ActivityResultLauncher<Intent> consent;
    private GoogleSignInAccount account;

    public GoogleCalendarImport(AppCompatActivity activity, Callback<Integer> onDone) {
        this.activity = activity;
        this.onDone = onDone;
        signIn = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> onSignedIn(result.getData()));
        // the first time Google asks the user to allow calendar access, then we try again
        consent = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (account != null) {
                        importFor(account);
                    } else {
                        fail(new IllegalStateException("Calendar access was not granted"));
                    }
                });
    }

    public void start() {
        GoogleSignInAccount last = GoogleSignIn.getLastSignedInAccount(activity);
        Scope scope = new Scope(FREE_BUSY_SCOPE);
        if (last != null && GoogleSignIn.hasPermissions(last, scope)) {
            account = last;
            importFor(last);
            return;
        }
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(scope)
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(activity, options);
        signIn.launch(client.getSignInIntent());
    }

    private void onSignedIn(@Nullable Intent data) {
        if (data == null) {
            fail(new IllegalStateException("Google sign-in was cancelled"));
            return;
        }
        try {
            account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            importFor(account);
        } catch (ApiException error) {
            fail(error);
        }
    }

    private void importFor(GoogleSignInAccount signedIn) {
        if (signedIn.getAccount() == null) {
            fail(new IllegalStateException("Google did not return an account"));
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                String token = GoogleAuthUtil.getToken(activity, signedIn.getAccount(), "oauth2:" + FREE_BUSY_SCOPE);
                Calendar start = startOfTodayInBogota();
                Calendar end = (Calendar) start.clone();
                end.add(Calendar.DAY_OF_MONTH, DAYS);
                List<String[]> busy = GoogleFreeBusyClient.busyIntervals(token, utcIso(start), utcIso(end));
                MainThread.post(() -> Repositories.schedules().importCalendarAvailability(dateKey(start), DAYS, busy, onDone));
            } catch (UserRecoverableAuthException needsConsent) {
                MainThread.post(() -> consent.launch(needsConsent.getIntent()));
            } catch (Exception error) {
                fail(error);
            }
        });
    }

    private void fail(Exception error) {
        MainThread.post(() -> onDone.onError(error));
    }

    private static Calendar startOfTodayInBogota() {
        Calendar day = Calendar.getInstance(BOGOTA);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static String utcIso(Calendar time) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(time.getTime());
    }

    private static String dateKey(Calendar day) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(BOGOTA);
        return format.format(day.getTime());
    }
}
