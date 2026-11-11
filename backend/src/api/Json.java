package api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal JSON writer/parser so the API needs no external libraries. */
public final class Json {

    private Json() {
    }

    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(out, value);
        return out.toString();
    }

    private static void write(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
            return;
        }
        if (value instanceof String text) {
            quote(out, text);
            return;
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            out.append(value);
            return;
        }
        if (value instanceof Double number) {
            if (number.isNaN() || number.isInfinite()) {
                out.append("null");
            } else if (number == Math.rint(number) && Math.abs(number) < 1e15) {
                out.append(number.longValue());
            } else {
                out.append(number);
            }
            return;
        }
        if (value instanceof Number number) {
            out.append(number);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                write(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> items) {
            out.append('[');
            boolean first = true;
            for (Object item : items) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(out, item);
            }
            out.append(']');
            return;
        }
        quote(out, String.valueOf(value));
    }

    public static String quote(String text) {
        StringBuilder out = new StringBuilder();
        quote(out, text);
        return out.toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    /** Parses a JSON document into Map / List / String / Double / Boolean / null. Returns null for blank input. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        parser.skipWhitespace();
        if (parser.atEnd()) {
            return null;
        }
        Object value = parser.value();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("Unexpected content after JSON value");
        }
        return value;
    }

    private static final class Parser {

        private final String text;
        private int index;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return index >= text.length();
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        Object value() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return switch (text.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            index++; // {
            skipWhitespace();
            if (peek() == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = string();
                skipWhitespace();
                expect(':');
                map.put(key, value());
                skipWhitespace();
                char ch = next();
                if (ch == '}') {
                    return map;
                }
                if (ch != ',') {
                    throw new IllegalArgumentException("Expected , or }");
                }
            }
        }

        private List<Object> array() {
            List<Object> list = new ArrayList<>();
            index++; // [
            skipWhitespace();
            if (peek() == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(value());
                skipWhitespace();
                char ch = next();
                if (ch == ']') {
                    return list;
                }
                if (ch != ',') {
                    throw new IllegalArgumentException("Expected , or ]");
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char ch = next();
                if (ch == '"') {
                    return out.toString();
                }
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                char escape = next();
                switch (escape) {
                    case '"', '\\', '/' -> out.append(escape);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) {
                            throw new IllegalArgumentException("Bad unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("Bad escape sequence");
                }
            }
        }

        private Object number() {
            int start = index;
            while (!atEnd() && "-+.eE0123456789".indexOf(text.charAt(index)) >= 0) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("Unexpected character '" + text.charAt(index) + "'");
            }
            return Double.valueOf(text.substring(start, index));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, index)) {
                throw new IllegalArgumentException("Unexpected literal");
            }
            index += word.length();
            return value;
        }

        private char peek() {
            if (atEnd()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            return text.charAt(index);
        }

        private char next() {
            char ch = peek();
            index++;
            return ch;
        }

        private void expect(char expected) {
            if (next() != expected) {
                throw new IllegalArgumentException("Expected '" + expected + "'");
            }
        }
    }
}
