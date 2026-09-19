package lexscope;

import java.util.Arrays;
import java.util.List;
import static lexscope.Ast.*;

/**
 * Runtime activation of one static scope. Holds one mutable cell per declared
 * slot; cells of Let bindings stay UNINITIALIZED until their statement runs.
 * Closures capture these activations, so captured bindings stay alive and
 * mutable after the defining scope finishes.
 */
final class Environment {
    private static final Object UNINITIALIZED = new Object();

    final Environment parent;
    final Resolver.Scope scope;
    final Object[] cells;

    Environment(Environment parent, Resolver.Scope scope) {
        this.parent = parent;
        this.scope = scope;
        this.cells = new Object[scope.size()];
        Arrays.fill(this.cells, UNINITIALIZED);
        List<Stmt> declarations = scope.declarations;
        for (int slot = 0; slot < declarations.size(); slot++) {
            if (declarations.get(slot) instanceof Fun fun)
                cells[slot] = new FunctionValue(fun, this);
        }
    }

    private Environment owner(Resolver.Ref ref) {
        Environment env = this;
        while (env.scope != ref.scope) env = env.parent;
        return env;
    }

    Object get(Resolver.Ref ref) {
        Object value = owner(ref).cells[ref.slot];
        if (value == UNINITIALIZED)
            throw new LangException("UNINITIALIZED", "Variable read before its Let ran");
        return value;
    }

    void assign(Resolver.Ref ref, Object value) {
        Environment env = owner(ref);
        if (env.cells[ref.slot] == UNINITIALIZED)
            throw new LangException("UNINITIALIZED", "Variable assigned before its Let ran");
        env.cells[ref.slot] = value;
    }

    /** Stores a Let's initializer result; the cell becomes readable. */
    void initialize(Resolver.Ref ref, Object value) {
        owner(ref).cells[ref.slot] = value;
    }
}
