package com.example.callblocker;

public final class PhoneNormalizer {
    private PhoneNormalizer() {}

    public static String normalize(String input) {
        if (input == null) return "";

        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '0' && c <= '9') {
                ascii.append(c);
            } else if (c >= '\u06F0' && c <= '\u06F9') { // Persian digits
                ascii.append((char) ('0' + (c - '\u06F0')));
            } else if (c >= '\u0660' && c <= '\u0669') { // Arabic-Indic digits
                ascii.append((char) ('0' + (c - '\u0660')));
            }
        }

        String n = ascii.toString();
        if (n.startsWith("0098") && n.length() > 4) {
            n = "0" + n.substring(4);
        } else if (n.startsWith("98") && n.length() >= 12) {
            n = "0" + n.substring(2);
        }
        return n;
    }

    public static ParsedRule parseRule(String input) {
        if (input == null) return new ParsedRule("", false);
        String trimmed = input.trim();
        boolean prefix = trimmed.endsWith("*");
        if (prefix) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return new ParsedRule(normalize(trimmed), prefix);
    }

    public static final class ParsedRule {
        public final String pattern;
        public final boolean prefix;

        ParsedRule(String pattern, boolean prefix) {
            this.pattern = pattern;
            this.prefix = prefix;
        }
    }
}
