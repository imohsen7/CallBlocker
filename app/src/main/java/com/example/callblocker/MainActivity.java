package com.example.callblocker;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private TextView statusBadge;
    private TextView rulesCountText;
    private TextView logsCountText;
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
        scroll.setBackgroundColor(0xFFF5F7FB);

        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(16), dp(16), dp(28));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout heroCard = card(0xFFFFFFFF, 0xFFE2E8F0, 22, 18);
        heroCard.addView(titleText("🛡️ مسدودکننده تماس"));

        TextView subtitle = bodyText("شماره کامل یا پیش‌شماره را تعریف کن؛ تماس‌های مطابق قانون به‌صورت خودکار رد می‌شوند و در لاگ داخلی ثبت می‌شوند.");
        subtitle.setPadding(0, dp(10), 0, dp(14));
        heroCard.addView(subtitle);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        statusText = new TextView(this);
        statusText.setTextSize(17);
        statusText.setTypeface(Typeface.DEFAULT_BOLD);
        statusText.setTextColor(0xFF111827);
        statusRow.addView(statusText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        statusBadge = badge("—", 0xFFE5E7EB, 0xFF4B5563);
        statusRow.addView(statusBadge);
        heroCard.addView(statusRow);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        statsRow.setPadding(0, dp(14), 0, dp(2));

        rulesCountText = metricCard("قوانین", "0");
        logsCountText = metricCard("لاگ‌ها", "0");
        statsRow.addView(rulesCountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams lpGap = new LinearLayout.LayoutParams(dp(10), 1);
        View gap = new View(this);
        statsRow.addView(gap, lpGap);
        statsRow.addView(logsCountText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        heroCard.addView(statsRow);

        roleButton = primaryButton("فعال‌سازی مسدودکننده تماس");
        roleButton.setOnClickListener(v -> toggleCallBlocker());
        heroCard.addView(roleButton, matchWrapMargins(0, 14, 0, 10));

        contactsButton = secondaryButton("اجازه بررسی شماره‌های ذخیره‌شده");
        contactsButton.setOnClickListener(v -> requestContactsPermission());
        heroCard.addView(contactsButton);

        root.addView(heroCard);

        LinearLayout addRuleCard = sectionCard("افزودن قانون", "برای پیش‌شماره در انتها * بگذار؛ مثل 092130*");

        ruleInput = new EditText(this);
        styleInput(ruleInput, "مثال: 09131101212 یا 092130*");
        addRuleCard.addView(ruleInput, matchWrapMargins(0, 8, 0, 10));

        Button add = primaryButton("افزودن قانون جدید");
        add.setOnClickListener(v -> addRule());
        addRuleCard.addView(add);
        root.addView(addRuleCard, matchWrapMargins(0, 14, 0, 0));

        LinearLayout rulesCard = sectionCard("قوانین مسدودسازی", "شماره‌ها و پیش‌شماره‌هایی که باید ریجکت شوند");
        rulesContainer = vertical();
        rulesCard.addView(rulesContainer, matchWrapMargins(0, 6, 0, 0));
        root.addView(rulesCard, matchWrapMargins(0, 14, 0, 0));

        LinearLayout settingsCard = sectionCard("تنظیمات لاگ", "مدت نگهداری لاگ تماس‌های ردشده را مشخص کن");
        retentionSpinner = new Spinner(this);
        retentionSpinner.setBackground(makeRounded(0xFFF8FAFC, 0xFFD7DEEA, 16, 1));
        settingsCard.addView(retentionSpinner, matchWrapMargins(0, 8, 0, 0));
        root.addView(settingsCard, matchWrapMargins(0, 14, 0, 0));

        LinearLayout logCard = sectionCard("تماس‌های ردشده", "آخرین تماس‌هایی که توسط برنامه مسدود شده‌اند");
        Button clear = dangerButton("پاک کردن لاگ");
        clear.setOnClickListener(v -> confirmClearLogs());
        logCard.addView(clear, wrapWrapMargins(0, 4, 0, 8));

        logsContainer = vertical();
        logCard.addView(logsContainer);

        TextView foot = footnoteText("Retention هنگام باز شدن برنامه و همین‌طور موقع دریافت تماس اعمال می‌شود.");
        foot.setPadding(0, dp(12), 0, 0);
        logCard.addView(foot);
        root.addView(logCard, matchWrapMargins(0, 14, 0, 0));

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
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
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
            Toast.makeText(this, "قانون با موفقیت اضافه شد", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "این قانون قبلاً وجود دارد", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshRules() {
        rulesContainer.removeAllViews();
        List<DBHelper.Rule> rules = db.getRules();
        setMetricValue(rulesCountText, String.valueOf(rules.size()));

        if (rules.isEmpty()) {
            rulesContainer.addView(emptyState("هنوز قانونی تعریف نشده است.", "اولین شماره یا پیش‌شماره را از بخش بالا اضافه کن."));
            return;
        }

        for (DBHelper.Rule rule : rules) {
            LinearLayout row = card(0xFFF8FAFC, 0xFFE3E8F2, 16, 12);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            LinearLayout textWrap = vertical();
            String shown = rule.pattern + (rule.prefix ? "*" : "");
            TextView label = new TextView(this);
            label.setText(shown);
            label.setTextSize(17);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setTextColor(0xFF111827);
            label.setTextDirection(View.TEXT_DIRECTION_LTR);
            textWrap.addView(label);

            TextView sub = bodyText(rule.prefix ? "نوع قانون: پیش‌شماره" : "نوع قانون: شماره کامل");
            sub.setTextSize(13);
            sub.setPadding(0, dp(4), 0, 0);
            textWrap.addView(sub);

            row.addView(textWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            Button del = dangerButton("حذف");
            del.setOnClickListener(v -> {
                db.deleteRule(rule.id);
                refreshRules();
            });
            row.addView(del, wrapWrapMargins(8, 0, 0, 0));

            rulesContainer.addView(row, matchWrapMargins(0, 0, 0, 10));
        }
    }

    private void refreshLogs() {
        logsContainer.removeAllViews();
        List<DBHelper.BlockedLog> logs = db.getLogs(200);
        setMetricValue(logsCountText, String.valueOf(logs.size()));

        if (logs.isEmpty()) {
            logsContainer.addView(emptyState("تماس ردشده‌ای ثبت نشده است.", "بعد از اولین ریجکت، جزئیات تماس اینجا نمایش داده می‌شود."));
            return;
        }

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US);
        for (DBHelper.BlockedLog log : logs) {
            LinearLayout item = card(0xFFFFFFFF, 0xFFE5EAF3, 16, 12);

            TextView phone = new TextView(this);
            phone.setText(log.phone);
            phone.setTextSize(18);
            phone.setTypeface(Typeface.DEFAULT_BOLD);
            phone.setTextColor(0xFF111827);
            phone.setTextDirection(View.TEXT_DIRECTION_LTR);
            item.addView(phone);

            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            chips.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            chips.setPadding(0, dp(8), 0, dp(8));
            chips.addView(badge("قانون: " + log.matchedRule, 0xFFE8F0FE, 0xFF1D4ED8));
            View spacer = new View(this);
            chips.addView(spacer, new LinearLayout.LayoutParams(dp(8), 1));
            chips.addView(badge("رد شد", 0xFFFDECEC, 0xFFB42318));
            item.addView(chips);

            TextView time = footnoteText(fmt.format(new Date(log.createdAt)));
            item.addView(time);

            logsContainer.addView(item, matchWrapMargins(0, 0, 0, 10));
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
            statusText.setText("وضعیت فعلی: غیرفعال");
            setBadge(statusBadge, "غیرفعال", 0xFFF3F4F6, 0xFF4B5563);
            roleButton.setText("فعال‌سازی مسدودکننده تماس");
        } else if (blockerEnabled) {
            statusText.setText("وضعیت فعلی: محافظت فعال است");
            setBadge(statusBadge, "فعال ✓", 0xFFDCFCE7, 0xFF15803D);
            roleButton.setText("غیرفعال کردن مسدودکننده تماس");
        } else {
            statusText.setText("وضعیت فعلی: برنامه غیرفعال است");
            setBadge(statusBadge, "متوقف", 0xFFFEF3C7, 0xFFB45309);
            roleButton.setText("فعال کردن مسدودکننده تماس");
        }
        roleButton.setEnabled(true);

        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        contactsButton.setText(contacts ? "بررسی شماره‌های ذخیره‌شده: فعال ✓" : "اجازه بررسی شماره‌های ذخیره‌شده");
        contactsButton.setEnabled(!contacts);
        if (contacts) {
            contactsButton.setAlpha(0.75f);
        } else {
            contactsButton.setAlpha(1f);
        }
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

    private LinearLayout card(int bgColor, int strokeColor, int radiusDp, int paddingDp) {
        LinearLayout l = vertical();
        l.setBackground(makeRounded(bgColor, strokeColor, radiusDp, 1));
        l.setPadding(dp(paddingDp), dp(paddingDp), dp(paddingDp), dp(paddingDp));
        return l;
    }

    private GradientDrawable makeRounded(int fillColor, int strokeColor, int radiusDp, int strokeWidthDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setCornerRadius(dp(radiusDp));
        shape.setStroke(dp(strokeWidthDp), strokeColor);
        return shape;
    }

    private TextView titleText(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(27);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFF0F172A);
        return t;
    }

    private TextView bodyText(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(14);
        t.setLineSpacing(0f, 1.15f);
        t.setTextColor(0xFF475569);
        return t;
    }

    private TextView footnoteText(String value) {
        TextView t = bodyText(value);
        t.setTextSize(12);
        t.setTextColor(0xFF64748B);
        return t;
    }

    private TextView badge(String value, int bgColor, int textColor) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(12);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(textColor);
        t.setPadding(dp(10), dp(6), dp(10), dp(6));
        t.setBackground(makeRounded(bgColor, bgColor, 999, 1));
        return t;
    }

    private void setBadge(TextView target, String value, int bgColor, int textColor) {
        target.setText(value);
        target.setTextColor(textColor);
        target.setBackground(makeRounded(bgColor, bgColor, 999, 1));
    }

    private TextView metricCard(String label, String value) {
        TextView t = new TextView(this);
        t.setText(metricText(label, value));
        t.setTextColor(0xFF0F172A);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setBackground(makeRounded(0xFFF8FAFC, 0xFFE2E8F0, 18, 1));
        t.setPadding(dp(12), dp(14), dp(12), dp(14));
        return t;
    }

    private void setMetricValue(TextView textView, String value) {
        CharSequence current = textView.getText();
        String source = current == null ? "" : current.toString();
        String label = source.contains("\n") ? source.substring(0, source.indexOf("\n")) : source;
        textView.setText(metricText(label, value));
    }

    private String metricText(String label, String value) {
        return label + "\n" + value;
    }

    private LinearLayout sectionCard(String title, String subtitle) {
        LinearLayout l = card(0xFFFFFFFF, 0xFFE2E8F0, 22, 16);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(19);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(0xFF0F172A);
        l.addView(titleView);

        TextView subtitleView = bodyText(subtitle);
        subtitleView.setPadding(0, dp(4), 0, dp(2));
        l.addView(subtitleView);
        return l;
    }

    private void styleInput(EditText editText, String hint) {
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextDirection(View.TEXT_DIRECTION_LTR);
        editText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        editText.setInputType(InputType.TYPE_CLASS_PHONE);
        editText.setTextSize(16);
        editText.setPadding(dp(14), dp(14), dp(14), dp(14));
        editText.setBackground(makeRounded(0xFFF8FAFC, 0xFFD7DEEA, 18, 1));
    }

    private Button primaryButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(0xFFFFFFFF);
        b.setBackground(makeRounded(0xFF2563EB, 0xFF2563EB, 16, 1));
        return b;
    }

    private Button secondaryButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(0xFF1D4ED8);
        b.setBackground(makeRounded(0xFFEFF6FF, 0xFFBFDBFE, 16, 1));
        return b;
    }

    private Button dangerButton(String value) {
        Button b = baseButton(value);
        b.setTextColor(0xFFB42318);
        b.setBackground(makeRounded(0xFFFEF2F2, 0xFFFECACA, 14, 1));
        return b;
    }

    private Button baseButton(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        return b;
    }

    private LinearLayout emptyState(String title, String subtitle) {
        LinearLayout l = card(0xFFF8FAFC, 0xFFE2E8F0, 16, 14);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setTextColor(0xFF334155);
        titleView.setTextSize(15);
        l.addView(titleView);

        TextView sub = footnoteText(subtitle);
        sub.setPadding(0, dp(4), 0, 0);
        l.addView(sub);
        return l;
    }

    private LinearLayout.LayoutParams matchWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams wrapWrapMargins(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
