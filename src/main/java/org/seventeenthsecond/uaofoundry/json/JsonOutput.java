package org.seventeenthsecond.uaofoundry.json;

import java.util.Map;
import java.util.stream.Collectors;

public final class JsonOutput {
    private JsonOutput() {
    }

    public static String object(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 2);
        escaped.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (c < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }
        escaped.append('"');
        return escaped.toString();
    }
}
