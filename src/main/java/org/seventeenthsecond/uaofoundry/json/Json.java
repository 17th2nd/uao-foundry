package org.seventeenthsecond.uaofoundry.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Minimal dependency-free JSON reader/writer used by the Foundry bootstrap surface.
 * Objects are emitted with lexicographically ordered keys. Array order is preserved.
 */
public final class Json {
    private Json() {}

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected trailing JSON content at offset " + parser.index());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value, String description) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(description + " must be a JSON object.");
        }
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value, String description) {
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException(description + " must be a JSON array.");
        }
        return (List<Object>) list;
    }

    public static String canonical(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, true, 0);
        return out.toString();
    }

    public static String pretty(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out, false, 0);
        out.append('\n');
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, boolean compact, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            quote(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b ? "true" : "false");
        } else if (value instanceof BigDecimal n) {
            BigDecimal normalized = n.stripTrailingZeros();
            if (normalized.compareTo(BigDecimal.ZERO) == 0) normalized = BigDecimal.ZERO;
            out.append(normalized.toPlainString());
        } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            out.append(value);
        } else if (value instanceof Float || value instanceof Double) {
            BigDecimal normalized = BigDecimal.valueOf(((Number) value).doubleValue()).stripTrailingZeros();
            out.append(normalized.toPlainString());
        } else if (value instanceof Map<?, ?> raw) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON object keys must be strings.");
                }
                sorted.put(key, entry.getValue());
            }
            out.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) out.append(',');
                if (!compact) newlineIndent(out, depth + 1);
                quote(entry.getKey(), out);
                out.append(compact ? ':' : ": ");
                write(entry.getValue(), out, compact, depth + 1);
                first = false;
            }
            if (!compact && !sorted.isEmpty()) newlineIndent(out, depth);
            out.append('}');
        } else if (value instanceof Collection<?> values) {
            out.append('[');
            boolean first = true;
            for (Object item : values) {
                if (!first) out.append(',');
                if (!compact) newlineIndent(out, depth + 1);
                write(item, out, compact, depth + 1);
                first = false;
            }
            if (!compact && !values.isEmpty()) newlineIndent(out, depth);
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }
    }

    private static void newlineIndent(StringBuilder out, int depth) {
        out.append('\n');
        out.append("  ".repeat(depth));
    }

    private static void quote(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private int index() { return index; }
        private boolean atEnd() { return index >= text.length(); }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) throw error("Unexpected end of JSON input");
            return switch (text.charAt(index)) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> parseLiteral("true", Boolean.TRUE);
                case 'f' -> parseLiteral("false", Boolean.FALSE);
                case 'n' -> parseLiteral("null", null);
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) { index++; return map; }
            while (true) {
                skipWhitespace();
                if (!peek('"')) throw error("Expected JSON object key");
                String key = parseString();
                if (map.containsKey(key)) throw error("Duplicate JSON object key: " + key);
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (peek('}')) { index++; return map; }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) { index++; return list; }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (peek(']')) { index++; return list; }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char c = text.charAt(index++);
                if (c == '"') return out.toString();
                if (c != '\\') {
                    if (c < 0x20) throw error("Unescaped control character in string");
                    out.append(c);
                    continue;
                }
                if (atEnd()) throw error("Unterminated escape sequence");
                char escaped = text.charAt(index++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(parseUnicodeEscape());
                    default -> throw error("Unsupported escape sequence: \\" + escaped);
                }
            }
            throw error("Unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > text.length()) throw error("Incomplete unicode escape");
            String hex = text.substring(index, index + 4);
            index += 4;
            try { return (char) Integer.parseInt(hex, 16); }
            catch (NumberFormatException ex) { throw error("Invalid unicode escape: " + hex); }
        }

        private Object parseLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) throw error("Expected " + literal);
            index += literal.length();
            return value;
        }

        private BigDecimal parseNumber() {
            int start = index;
            if (peek('-')) index++;
            if (atEnd()) throw error("Invalid number");
            if (peek('0')) index++;
            else {
                if (!isDigit(text.charAt(index))) throw error("Invalid number");
                while (!atEnd() && isDigit(text.charAt(index))) index++;
            }
            if (!atEnd() && peek('.')) {
                index++;
                if (atEnd() || !isDigit(text.charAt(index))) throw error("Invalid fractional number");
                while (!atEnd() && isDigit(text.charAt(index))) index++;
            }
            if (!atEnd() && (peek('e') || peek('E'))) {
                index++;
                if (!atEnd() && (peek('+') || peek('-'))) index++;
                if (atEnd() || !isDigit(text.charAt(index))) throw error("Invalid exponent");
                while (!atEnd() && isDigit(text.charAt(index))) index++;
            }
            try { return new BigDecimal(text.substring(start, index)); }
            catch (NumberFormatException ex) { throw error("Invalid number"); }
        }

        private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        private boolean peek(char c) { return !atEnd() && text.charAt(index) == c; }
        private void expect(char c) {
            skipWhitespace();
            if (atEnd() || text.charAt(index) != c) throw error("Expected '" + c + "'");
            index++;
        }
        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index + ".");
        }
    }
}
