package com.example.callblocker;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DBHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "call_blocker.db";
    private static final int DB_VERSION = 2;

    public static final String ACTION_BLOCK = "block";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_SILENCE = "silence";

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pattern TEXT NOT NULL," +
                "is_prefix INTEGER NOT NULL DEFAULT 0," +
                "action TEXT NOT NULL DEFAULT 'block'," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(pattern, is_prefix))");

        db.execSQL("CREATE TABLE blocked_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "phone_raw TEXT," +
                "phone_normalized TEXT NOT NULL," +
                "matched_rule TEXT NOT NULL," +
                "action TEXT NOT NULL DEFAULT 'block'," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_logs_created_at ON blocked_logs(created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE rules ADD COLUMN action TEXT NOT NULL DEFAULT 'reject'");
            db.execSQL("ALTER TABLE blocked_logs ADD COLUMN action TEXT NOT NULL DEFAULT 'reject'");
        }
    }

    public boolean addRule(String text) {
        return addRule(text, ACTION_BLOCK);
    }

    public boolean addRule(String text, String action) {
        PhoneNormalizer.ParsedRule rule = PhoneNormalizer.parseRule(text);
        if (rule.pattern.length() < 3) return false;

        ContentValues v = new ContentValues();
        v.put("pattern", rule.pattern);
        v.put("is_prefix", rule.prefix ? 1 : 0);
        v.put("action", sanitizeAction(action));
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "rules", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public void deleteRule(long id) {
        getWritableDatabase().delete("rules", "id=?", new String[]{String.valueOf(id)});
    }

    public void updateRuleAction(long id, String action) {
        ContentValues v = new ContentValues();
        v.put("action", sanitizeAction(action));
        getWritableDatabase().update("rules", v, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Rule> getRules() {
        ArrayList<Rule> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, pattern, is_prefix, action FROM rules ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                out.add(new Rule(
                        c.getLong(0),
                        c.getString(1),
                        c.getInt(2) == 1,
                        sanitizeAction(c.getString(3))
                ));
            }
        }
        return out;
    }

    public RuleMatch findMatchingRule(String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.isEmpty()) return null;

        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT pattern, is_prefix, action FROM rules " +
                        "ORDER BY is_prefix ASC, LENGTH(pattern) DESC", null)) {
            while (c.moveToNext()) {
                String pattern = c.getString(0);
                boolean prefix = c.getInt(1) == 1;
                String action = sanitizeAction(c.getString(2));
                if ((!prefix && normalizedNumber.equals(pattern)) ||
                        (prefix && normalizedNumber.startsWith(pattern))) {
                    return new RuleMatch(prefix ? pattern + "*" : pattern, action);
                }
            }
        }
        return null;
    }

    public void addBlockedLog(String raw, String normalized, String matchedRule) {
        addBlockedLog(raw, normalized, matchedRule, ACTION_BLOCK);
    }

    public void addBlockedLog(String raw, String normalized, String matchedRule, String action) {
        ContentValues v = new ContentValues();
        v.put("phone_raw", raw);
        v.put("phone_normalized", normalized);
        v.put("matched_rule", matchedRule);
        v.put("action", sanitizeAction(action));
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("blocked_logs", null, v);
    }

    public List<BlockedLog> getLogs(int limit) {
        ArrayList<BlockedLog> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, phone_normalized, matched_rule, action, created_at " +
                        "FROM blocked_logs ORDER BY created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(new BlockedLog(
                        c.getLong(0),
                        c.getString(1),
                        c.getString(2),
                        sanitizeAction(c.getString(3)),
                        c.getLong(4)
                ));
            }
        }
        return out;
    }

    public void clearLogs() {
        getWritableDatabase().delete("blocked_logs", null, null);
    }

    public void cleanupLogs(int retentionDays) {
        if (retentionDays <= 0) return; // 0 = keep forever
        long cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L;
        getWritableDatabase().delete("blocked_logs", "created_at < ?", new String[]{String.valueOf(cutoff)});
    }

    public static String sanitizeAction(String action) {
        if (ACTION_REJECT.equals(action)) return ACTION_REJECT;
        if (ACTION_SILENCE.equals(action)) return ACTION_SILENCE;
        return ACTION_BLOCK;
    }

    public static final class Rule {
        public final long id;
        public final String pattern;
        public final boolean prefix;
        public final String action;

        Rule(long id, String pattern, boolean prefix, String action) {
            this.id = id;
            this.pattern = pattern;
            this.prefix = prefix;
            this.action = action;
        }
    }

    public static final class RuleMatch {
        public final String matchedRule;
        public final String action;

        RuleMatch(String matchedRule, String action) {
            this.matchedRule = matchedRule;
            this.action = action;
        }
    }

    public static final class BlockedLog {
        public final long id;
        public final String phone;
        public final String matchedRule;
        public final String action;
        public final long createdAt;

        BlockedLog(long id, String phone, String matchedRule, String action, long createdAt) {
            this.id = id;
            this.phone = phone;
            this.matchedRule = matchedRule;
            this.action = action;
            this.createdAt = createdAt;
        }
    }
}
