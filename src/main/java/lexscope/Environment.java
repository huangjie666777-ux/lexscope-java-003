package lexscope;

import java.util.HashMap;
import java.util.Map;

/** Current name-based environment chain. */
final class Environment {
    final Environment parent;
    final Map<String, Object> values = new HashMap<>();
    Environment(Environment parent) { this.parent = parent; }
    void define(String name, Object value) { values.put(name, value); }
    Object get(String name) {
        if (values.containsKey(name)) return values.get(name);
        if (parent != null) return parent.get(name);
        throw new LangException("RESOLVE", "Unknown name: " + name);
    }
    void assign(String name, Object value) {
        if (values.containsKey(name)) { values.put(name, value); return; }
        if (parent != null) { parent.assign(name, value); return; }
        throw new LangException("RESOLVE", "Unknown assignment: " + name);
    }
}
