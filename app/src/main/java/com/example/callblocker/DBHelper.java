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
    private static final int DB_VERSION = 1;

    public DBHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "pattern TEXT NOT NULL," +
                "is_prefix INTEGER NOT NULL DEFAULT 0," +
                "created_at INTEGER NOT NULL," +
                "UNIQUE(pattern, is_prefix))");

        db.execSQL("CREATE TABLE blocked_logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "phone_raw TEXT," +
                "phone_normalized TEXT NOT NULL," +
                "matched_rule TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_logs_created_at ON blocked_logs(created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migration needed in version 1.
    }

    public boolean addRule(String text) {
        PhoneNormalizer.ParsedRule rule = PhoneNormalizer.parseRule(text);
        if (rule.pattern.length() < 3) return false;

        ContentValues v = new ContentValues();
        v.put("pattern", rule.pattern);
        v.put("is_prefix", rule.prefix ? 1 : 0);
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insertWithOnConflict(
                "rules", null, v, SQLiteDatabase.CONFLICT_IGNORE) != -1;
    }

    public void deleteRule(long id) {
        getWritableDatabase().delete("rules", "id=?", new String[]{String.valueOf(id)});
    }

    public List<Rule> getRules() {
        ArrayList<Rule> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, pattern, is_prefix FROM rules ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                out.add(new Rule(c.getLong(0), c.getString(1), c.getInt(2) == 1));
            }
        }
        return out;
    }

    public String findMatchingRule(String normalizedNumber) {
        if (normalizedNumber == null || normalizedNumber.isEmpty()) return null;

        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT pattern, is_prefix FROM rules ORDER BY is_prefix ASC, LENGTH(pattern) DESC", null)) {
            while (c.moveToNext()) {
                String pattern = c.getString(0);
                boolean prefix = c.getInt(1) == 1;
                if ((!prefix && normalizedNumber.equals(pattern)) ||
                        (prefix && normalizedNumber.startsWith(pattern))) {
                    return prefix ? pattern + "*" : pattern;
                }
            }
        }
        return null;
    }

    public void addBlockedLog(String raw, String normalized, String matchedRule) {
        ContentValues v = new ContentValues();
        v.put("phone_raw", raw);
        v.put("phone_normalized", normalized);
        v.put("matched_rule", matchedRule);
        v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("blocked_logs", null, v);
    }

    public List<BlockedLog> getLogs(int limit) {
        ArrayList<BlockedLog> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, phone_normalized, matched_rule, created_at " +
                        "FROM blocked_logs ORDER BY created_at DESC LIMIT ?",
                new String[]{String.valueOf(limit)})) {
            while (c.moveToNext()) {
                out.add(new BlockedLog(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
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

    public static final class Rule {
        public final long id;
        public final String pattern;
        public final boolean prefix;

        Rule(long id, String pattern, boolean prefix) {
            this.id = id;
            this.pattern = pattern;
            this.prefix = prefix;
        }
    }

    public static final class BlockedLog {
        public final long id;
        public final String phone;
        public final String matchedRule;
        public final long createdAt;

        BlockedLog(long id, String phone, String matchedRule, long createdAt) {
            this.id = id;
            this.phone = phone;
            this.matchedRule = matchedRule;
            this.createdAt = createdAt;
        }
    }
}
