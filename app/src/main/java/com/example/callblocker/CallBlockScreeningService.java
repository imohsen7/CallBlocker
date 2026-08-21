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

        // Enforce retention opportunistically on every screened incoming call.
        int retentionDays = prefs.getInt("retention_days", 30);
        db.cleanupLogs(retentionDays);

        String matchedRule = db.findMatchingRule(normalized);
        boolean shouldBlock = matchedRule != null;

        CallResponse.Builder builder = new CallResponse.Builder();
        if (shouldBlock) {
            builder.setDisallowCall(true)
                    .setRejectCall(true)
                    .setSkipNotification(true);

            db.addBlockedLog(rawNumber, normalized, matchedRule);
        } else {
            builder.setDisallowCall(false)
                    .setRejectCall(false);
        }

        respondToCall(callDetails, builder.build());
        db.close();
    }
}
