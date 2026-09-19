package lexscope;

import java.util.HashMap;
import java.util.Map;

/** One runtime scope instance: a chain of frames keyed by resolved slots. */
final class Environment {
    final Environment parent;
    final Map<Slot, Cell> cells = new HashMap<>();
    Environment(Environment parent) { this.parent = parent; }
}

/** A mutable binding cell; starts uninitialized for Let declarations. */
final class Cell {
    private Object value;
    private boolean initialized;

    Cell() {}

    static Cell initialized(Object value) {
        Cell cell = new Cell();
        cell.value = value;
        cell.initialized = true;
        return cell;
    }

    boolean isInitialized() { return initialized; }
    Object value() { return value; }
    void initialize(Object newValue) { value = newValue; initialized = true; }
}
