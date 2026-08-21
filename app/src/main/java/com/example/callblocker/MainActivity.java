package com.example.callblocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_ROLE = 1001;
    private static final int REQ_CONTACTS = 1002;

    private DBHelper db;
    private SharedPreferences prefs;
    private TextView statusText;
    private Button roleButton;
    private Button contactsButton;
    private LinearLayout rulesContainer;
    private LinearLayout logsContainer;
    private EditText ruleInput;
    private Spinner retentionSpinner;

    private final int[] retentionValues = {7, 30, 90, 365, 0};
    private final String[] retentionLabels = {"۷ روز", "۳۰ روز", "۹۰ روز", "۱ سال", "همیشه"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new DBHelper(this);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        setContentView(buildUi());
        setupRetention();
    }

    @Override
    protected void onResume() {
        super.onResume();
        int retention = prefs.getInt("retention_days", 30);
        db.cleanupLogs(retention);
        refreshStatus();
        refreshRules();
        refreshLogs();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        LinearLayout root = vertical();
        root.setPadding(dp(18), dp(18), dp(18), dp(32));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("مسدودکننده تماس", 26, true);
        root.addView(title);

        TextView desc = text("شماره کامل یا پیش‌شماره وارد کنید. برای پیش‌شماره در انتها * بگذارید؛ مثال: 092130*", 15, false);
        desc.setPadding(0, dp(8), 0, dp(18));
        root.addView(desc);

        statusText = text("", 16, true);
        root.addView(statusText);

        roleButton = button("فعال‌سازی مسدودکننده تماس");
        roleButton.setOnClickListener(v -> toggleCallBlocker());
        root.addView(roleButton, matchWrapMargins(0, 8, 0, 8));

        contactsButton = button("اجازه بررسی شماره‌های ذخیره‌شده");
        contactsButton.setOnClickListener(v -> requestContactsPermission());
        root.addView(contactsButton, matchWrapMargins(0, 0, 0, 22));

        root.addView(section("قوانین مسدودسازی"));

        ruleInput = new EditText(this);
        ruleInput.setHint("مثال: 09131101212 یا 092130*");
        ruleInput.setSingleLine(true);
        ruleInput.setTextDirection(View.TEXT_DIRECTION_LTR);
        ruleInput.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        ruleInput.setInputType(InputType.TYPE_CLASS_PHONE);
        root.addView(ruleInput, matchWrapMargins(0, 8, 0, 8));

        Button add = button("افزودن قانون");
        add.setOnClickListener(v -> addRule());
        root.addView(add, matchWrapMargins(0, 0, 0, 12));

        rulesContainer = vertical();
        root.addView(rulesContainer);

        root.addView(section("نگهداری لاگ"));
        retentionSpinner = new Spinner(this);
        root.addView(retentionSpinner, matchWrapMargins(0, 8, 0, 18));

        LinearLayout logHeader = new LinearLayout(this);
        logHeader.setOrientation(LinearLayout.HORIZONTAL);
        logHeader.setGravity(Gravity.CENTER_VERTICAL);
        logHeader.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView logTitle = section("تماس‌های ردشده");
        logHeader.addView(logTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button clear = button("پاک کردن لاگ");
        clear.setOnClickListener(v -> confirmClearLogs());
        logHeader.addView(clear);
        root.addView(logHeader);

        logsContainer = vertical();
        root.addView(logsContainer);

        TextView foot = text("Retention هنگام باز شدن برنامه و هنگام دریافت تماس اعمال می‌شود.", 12, false);
        foot.setPadding(0, dp(18), 0, 0);
        root.addView(foot);

        return scroll;
    }

    private void setupRetention() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, retentionLabels);
        retentionSpinner.setAdapter(adapter);

        int current = prefs.getInt("retention_days", 30);
        int selected = 1;
        for (int i = 0; i < retentionValues.length; i++) {
            if (retentionValues[i] == current) selected = i;
        }
        retentionSpinner.setSelection(selected, false);
        retentionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                int days = retentionValues[position];
                prefs.edit().putInt("retention_days", days).apply();
                db.cleanupLogs(days);
                refreshLogs();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void addRule() {
        String input = ruleInput.getText().toString().trim();
        PhoneNormalizer.ParsedRule parsed = PhoneNormalizer.parseRule(input);
        if (parsed.pattern.length() < 3) {
            Toast.makeText(this, "شماره یا پیش‌شماره معتبر وارد کنید", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean added = db.addRule(input);
        if (added) {
            ruleInput.setText("");
            refreshRules();
        } else {
            Toast.makeText(this, "این قانون قبلاً وجود دارد", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshRules() {
        rulesContainer.removeAllViews();
        List<DBHelper.Rule> rules = db.getRules();
        if (rules.isEmpty()) {
            TextView empty = text("هنوز قانونی تعریف نشده است.", 14, false);
            empty.setPadding(0, dp(4), 0, dp(14));
            rulesContainer.addView(empty);
            return;
        }

        for (DBHelper.Rule rule : rules) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(5), 0, dp(5));
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            String shown = rule.pattern + (rule.prefix ? "*" : "");
            TextView label = text(shown + (rule.prefix ? "  — پیش‌شماره" : "  — شماره کامل"), 16, false);
            label.setTextDirection(View.TEXT_DIRECTION_LTR);
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button del = button("حذف");
            del.setOnClickListener(v -> {
                db.deleteRule(rule.id);
                refreshRules();
            });
            row.addView(del);
            rulesContainer.addView(row);
        }
    }

    private void refreshLogs() {
        logsContainer.removeAllViews();
        List<DBHelper.BlockedLog> logs = db.getLogs(200);
        if (logs.isEmpty()) {
            TextView empty = text("تماس ردشده‌ای ثبت نشده است.", 14, false);
            empty.setPadding(0, dp(8), 0, 0);
            logsContainer.addView(empty);
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US);
        for (DBHelper.BlockedLog log : logs) {
            LinearLayout item = vertical();
            item.setPadding(0, dp(8), 0, dp(8));

            TextView phone = text(log.phone, 17, true);
            phone.setTextDirection(View.TEXT_DIRECTION_LTR);
            item.addView(phone);
            item.addView(text("قانون: " + log.matchedRule, 13, false));
            item.addView(text(fmt.format(new Date(log.createdAt)), 13, false));

            View divider = new View(this);
            divider.setBackgroundColor(0xFFE0E0E0);
            item.addView(divider, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            logsContainer.addView(item);
        }
    }

    private void confirmClearLogs() {
        new AlertDialog.Builder(this)
                .setTitle("پاک کردن لاگ")
                .setMessage("همه تماس‌های ردشده از لاگ داخلی برنامه حذف شوند؟")
                .setPositiveButton("بله", (d, w) -> {
                    db.clearLogs();
                    refreshLogs();
                })
                .setNegativeButton("خیر", null)
                .show();
    }

    private void toggleCallBlocker() {
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        if (rm == null || !rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            Toast.makeText(this, "Call Screening روی این دستگاه در دسترس نیست", Toast.LENGTH_LONG).show();
            return;
        }

        if (!rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            Intent intent = rm.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
            startActivityForResult(intent, REQ_ROLE);
            return;
        }

        boolean enabled = prefs.getBoolean("blocker_enabled", true);
        prefs.edit().putBoolean("blocker_enabled", !enabled).apply();
        refreshStatus();
        Toast.makeText(this,
                enabled ? "مسدودکننده تماس غیرفعال شد" : "مسدودکننده تماس فعال شد",
                Toast.LENGTH_SHORT).show();
    }

    private void requestContactsPermission() {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "مجوز Contacts قبلاً داده شده است", Toast.LENGTH_SHORT).show();
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, REQ_CONTACTS);
        }
    }

    private void refreshStatus() {
        RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
        boolean roleHeld = rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING);
        boolean blockerEnabled = roleHeld && prefs.getBoolean("blocker_enabled", true);

        if (!roleHeld) {
            statusText.setText("وضعیت: غیرفعال");
            roleButton.setText("فعال‌سازی مسدودکننده تماس");
        } else if (blockerEnabled) {
            statusText.setText("وضعیت: فعال ✓");
            roleButton.setText("غیرفعال کردن مسدودکننده تماس");
        } else {
            statusText.setText("وضعیت: غیرفعال");
            roleButton.setText("فعال کردن مسدودکننده تماس");
        }
        roleButton.setEnabled(true);

        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        contactsButton.setText(contacts ? "بررسی شماره‌های ذخیره‌شده: فعال ✓" : "اجازه بررسی شماره‌های ذخیره‌شده");
        contactsButton.setEnabled(!contacts);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            RoleManager rm = (RoleManager) getSystemService(ROLE_SERVICE);
            if (rm != null && rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                prefs.edit().putBoolean("blocker_enabled", true).apply();
            }
            refreshStatus();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CONTACTS) refreshStatus();
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(0xFF202124);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView section(String value) {
        TextView t = text(value, 20, true);
        t.setPadding(0, dp(18), 0, dp(4));
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        return b;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
