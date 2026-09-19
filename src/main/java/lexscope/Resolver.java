package lexscope;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import static lexscope.Ast.*;

/**
 * Static resolution pass that runs before any evaluation or output.
 * Binds every Var/Assign/Let occurrence to a unique declaration slot and
 * records the declarations of each program/block/function scope.
 */
final class Resolver {
    private final IdentityHashMap<Object, Slot> slots = new IdentityHashMap<>();
    private final IdentityHashMap<Object, ScopeInfo> scopes = new IdentityHashMap<>();
    private final Deque<Map<String, Slot>> stack = new ArrayDeque<>();
    private int functionDepth;

    static Resolution resolve(List<Stmt> program) {
        Resolver resolver = new Resolver();
        resolver.scope(program, program, List.of(), false);
        return new Resolution(resolver.slots, resolver.scopes);
    }

    private void declare(Map<String, Slot> names, String name, List<Slot> bucket) {
        if (names.containsKey(name))
            throw new LangException("RESOLVE", "Duplicate declaration: " + name);
        Slot slot = new Slot(name);
        names.put(name, slot);
        if (bucket != null) bucket.add(slot);
    }

    private void scope(Object key, List<Stmt> body, List<String> params, boolean isFunction) {
        ScopeInfo info = new ScopeInfo();
        scopes.put(key, info);
        Map<String, Slot> names = new HashMap<>();
        for (String param : params) declare(names, param, info.params);
        for (Stmt statement : body) {
            if (statement instanceof Let s) {
                declare(names, s.name(), info.lets);
                slots.put(s, names.get(s.name()));
            } else if (statement instanceof Fun s) {
                declare(names, s.name(), null);
                info.functions.add(Map.entry(names.get(s.name()), s));
            }
        }
        stack.push(names);
        if (isFunction) functionDepth++;
        for (Stmt statement : body) statement(statement);
        if (isFunction) functionDepth--;
        stack.pop();
    }

    private void statement(Stmt statement) {
        if (statement instanceof Let s) {
            expression(s.initializer());
        } else if (statement instanceof Assign s) {
            slots.put(s, lookup(s.name()));
            expression(s.value());
        } else if (statement instanceof Fun s) {
            scope(s, s.body(), s.params(), true);
        } else if (statement instanceof Block s) {
            scope(s, s.body(), List.of(), false);
        } else if (statement instanceof If s) {
            expression(s.condition());
            statement(s.yes());
            statement(s.no());
        } else if (statement instanceof Return s) {
            if (functionDepth == 0)
                throw new LangException("RESOLVE", "Return outside a function");
            expression(s.value());
        } else if (statement instanceof Emit s) {
            expression(s.value());
        } else if (statement instanceof Eval s) {
            expression(s.value());
        } else throw new IllegalArgumentException("Unknown statement");
    }

    private void expression(Expr expression) {
        if (expression instanceof Num) {
        } else if (expression instanceof Var e) {
            slots.put(e, lookup(e.name()));
        } else if (expression instanceof Binary e) {
            expression(e.left());
            expression(e.right());
        } else if (expression instanceof Call e) {
            expression(e.callee());
            for (Expr arg : e.args()) expression(arg);
        } else throw new IllegalArgumentException("Unknown expression");
    }

    private Slot lookup(String name) {
        for (Map<String, Slot> names : stack) {
            Slot slot = names.get(name);
            if (slot != null) return slot;
        }
        throw new LangException("RESOLVE", "Undefined name: " + name);
    }
}

/** Identity of one declaration; structurally equal nodes get distinct slots. */
final class Slot {
    final String name;
    Slot(String name) { this.name = name; }
}

/** Direct declarations of one static scope, allocated on every scope entry. */
final class ScopeInfo {
    final List<Slot> params = new ArrayList<>();
    final List<Map.Entry<Slot, Ast.Fun>> functions = new ArrayList<>();
    final List<Slot> lets = new ArrayList<>();
}

/** Result of the static resolution pass for one execute run. */
final class Resolution {
    private final IdentityHashMap<Object, Slot> slots;
    private final IdentityHashMap<Object, ScopeInfo> scopes;

    Resolution(IdentityHashMap<Object, Slot> slots, IdentityHashMap<Object, ScopeInfo> scopes) {
        this.slots = slots;
        this.scopes = scopes;
    }

    Slot slotOf(Object node) { return slots.get(node); }
    ScopeInfo scopeOf(Object node) { return scopes.get(node); }
}
