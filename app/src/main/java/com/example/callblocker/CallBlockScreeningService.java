package com.example.callblocker;

import android.content.SharedPreferences;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.CallScreeningService;

public class CallBlockScreeningService extends CallScreeningService {

    @Override
    public void onScreenCall(Call.Details callDetails) {
        if (callDetails.getCallDirection() != Call.Details.DIRECTION_INCOMING) {
            return;
        }

        Uri handle = callDetails.getHandle();
        String rawNumber = handle != null ? handle.getSchemeSpecificPart() : "";
        String normalized = PhoneNormalizer.normalize(rawNumber);

        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);

        // Keep the system Call Screening role, but allow every call while the
        // user has disabled blocking from inside the app.
        if (!prefs.getBoolean("blocker_enabled", true)) {
            CallResponse response = new CallResponse.Builder()
                    .setDisallowCall(false)
                    .setRejectCall(false)
                    .build();
            respondToCall(callDetails, response);
            return;
        }

        DBHelper db = new DBHelper(getApplicationContext());
        try {
            // Enforce retention opportunistically on every screened incoming call.
            int retentionDays = prefs.getInt("retention_days", 30);
            db.cleanupLogs(retentionDays);

            DBHelper.RuleMatch match = db.findMatchingRule(normalized);
            CallResponse.Builder builder = new CallResponse.Builder();

            if (match == null) {
                builder.setDisallowCall(false)
                        .setRejectCall(false);
            } else if (DBHelper.ACTION_REJECT.equals(match.action)) {
                // Behaves like a manual reject from the user's point of view.
                builder.setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true);
                db.addBlockedLog(rawNumber, normalized, match.matchedRule, match.action);
            } else if (DBHelper.ACTION_SILENCE.equals(match.action)) {
                // The call is still presented to the dialer, but without ringing.
                builder.setDisallowCall(false)
                        .setRejectCall(false)
                        .setSilenceCall(true);
                db.addBlockedLog(rawNumber, normalized, match.matchedRule, match.action);
            } else {
                // Block without marking it as a manual user rejection.
                builder.setDisallowCall(true)
                        .setRejectCall(false)
                        .setSkipNotification(true);
                db.addBlockedLog(rawNumber, normalized, match.matchedRule, DBHelper.ACTION_BLOCK);
            }

            respondToCall(callDetails, builder.build());
        } finally {
            db.close();
        }
    }
}
