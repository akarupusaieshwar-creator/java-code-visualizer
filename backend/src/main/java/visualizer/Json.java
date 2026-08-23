package visualizer;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/** Tiny dependency-free JSON serializer (Map/List/String/Number/Boolean/null only). */
final class Json {

    private Json() {}

    static void write(Object value, Writer w) throws IOException {
        if (value == null) {
            w.write("null");
        } else if (value instanceof String) {
            writeString((String) value, w);
        } else if (value instanceof Character) {
            writeString(value.toString(), w);
        } else if (value instanceof Boolean || value instanceof Number) {
            w.write(value.toString());
        } else if (value instanceof Map) {
            writeMap((Map<?, ?>) value, w);
        } else if (value instanceof List) {
            writeList((List<?>) value, w);
        } else {
            writeString(value.toString(), w);
        }
    }

    private static void writeMap(Map<?, ?> map, Writer w) throws IOException {
        w.write('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) w.write(',');
            first = false;
            writeString(String.valueOf(e.getKey()), w);
            w.write(':');
            write(e.getValue(), w);
        }
        w.write('}');
    }

    private static void writeList(List<?> list, Writer w) throws IOException {
        w.write('[');
        boolean first = true;
        for (Object o : list) {
            if (!first) w.write(',');
            first = false;
            write(o, w);
        }
        w.write(']');
    }

    private static void writeString(String s, Writer w) throws IOException {
        w.write('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': w.write("\\\""); break;
                case '\\': w.write("\\\\"); break;
                case '\n': w.write("\\n"); break;
                case '\r': w.write("\\r"); break;
                case '\t': w.write("\\t"); break;
                default:
                    if (c < 0x20) {
                        w.write(String.format("\\u%04x", (int) c));
                    } else {
                        w.write(c);
                    }
            }
        }
        w.write('"');
    }
}
