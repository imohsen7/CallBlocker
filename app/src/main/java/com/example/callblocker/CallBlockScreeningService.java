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

        DBHelper db = new DBHelper(getApplicationContext());

        // Enforce retention opportunistically on every screened incoming call.
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
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
