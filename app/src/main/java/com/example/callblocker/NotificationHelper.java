package com.example.callblocker;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

public final class NotificationHelper {
    private static final String CHANNEL_ID = "call_blocker_status";
    private static final int NOTIFICATION_ID = 2201;

    private NotificationHelper() {}

    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        RoleManager rm = (RoleManager) app.getSystemService(Context.ROLE_SERVICE);
        boolean roleHeld = rm != null
                && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)
                && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);

        SharedPreferences prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean enabled = roleHeld && prefs.getBoolean("blocker_enabled", true);

        if (enabled) {
            show(app);
        } else {
            cancel(app);
        }
    }

    public static void show(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "وضعیت مسدودکننده تماس",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("نمایش فعال بودن مسدودکننده تماس");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_call_blocker_status)
                .setContentTitle("مسدودکننده تماس فعال است")
                .setContentText("تماس‌های مطابق قوانین به‌صورت خودکار رد می‌شوند")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();

        try {
            manager.notify(NOTIFICATION_ID, notification);
        } catch (SecurityException ignored) {
            // Notification permission can be disabled by the user at any time.
        }
    }

    public static void cancel(Context context) {
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
