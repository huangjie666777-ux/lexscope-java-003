package lexscope;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static lexscope.Ast.*;

/** AST evaluator. Resolution happens fully before any execution or output. */
public final class Engine {
    private Resolution resolution;
    private Consumer<Long> output;

    public void execute(List<Stmt> program, Consumer<Long> output) {
        Resolution resolved = Resolver.resolve(program);
        this.resolution = resolved;
        this.output = output;
        try {
            Environment global = new Environment(null);
            enterScope(resolved.scopeOf(program), global, List.of());
            statements(program, global);
        } catch (ReturnSignal signal) {
            throw new LangException("RESOLVE", "Return outside a function");
        } finally {
            this.resolution = null;
            this.output = null;
        }
    }

    Resolution resolution() { return resolution; }

    void enterScope(ScopeInfo info, Environment env, List<Object> arguments) {
        for (int i = 0; i < info.params.size(); i++)
            env.cells.put(info.params.get(i), Cell.initialized(arguments.get(i)));
        for (var entry : info.functions)
            env.cells.put(entry.getKey(), Cell.initialized(new FunctionValue(entry.getValue(), env)));
        for (Slot slot : info.lets)
            env.cells.put(slot, new Cell());
    }

    void statements(List<Stmt> body, Environment env) {
        for (Stmt statement : body) statement(statement, env);
    }

    private void statement(Stmt statement, Environment env) {
        if (statement instanceof Let s) {
            Object value = expression(s.initializer(), env);
            cell(env, resolution.slotOf(s)).initialize(value);
        } else if (statement instanceof Assign s) {
            Object value = expression(s.value(), env);
            Cell target = cell(env, resolution.slotOf(s));
            if (!target.isInitialized())
                throw new LangException("UNINITIALIZED", "Assignment before initialization: " + s.name());
            target.initialize(value);
        } else if (statement instanceof Fun) {
            // Function cells are initialized on scope entry; nothing to do here.
        } else if (statement instanceof Block s) {
            Environment child = new Environment(env);
            enterScope(resolution.scopeOf(s), child, List.of());
            statements(s.body(), child);
        } else if (statement instanceof If s) {
            statement(number(expression(s.condition(), env)) != 0 ? s.yes() : s.no(), env);
        } else if (statement instanceof Return s) {
            throw new ReturnSignal(expression(s.value(), env));
        } else if (statement instanceof Emit s) {
            output.accept(number(expression(s.value(), env)));
        } else if (statement instanceof Eval s) {
            expression(s.value(), env);
        } else throw new IllegalArgumentException("Unknown statement");
    }

    private Object expression(Expr expression, Environment env) {
        if (expression instanceof Num e) return e.value();
        if (expression instanceof Var e) {
            Cell source = cell(env, resolution.slotOf(e));
            if (!source.isInitialized())
                throw new LangException("UNINITIALIZED", "Read before initialization: " + e.name());
            return source.value();
        }
        if (expression instanceof Binary e) {
            long a = number(expression(e.left(), env));
            long b = number(expression(e.right(), env));
            return switch (e.op()) {
                case ADD -> a + b;
                case SUB -> a - b;
                case MUL -> a * b;
                case LE -> a <= b ? 1L : 0L;
            };
        }
        if (expression instanceof Call e) {
            Object callable = expression(e.callee(), env);
            List<Object> arguments = new ArrayList<>();
            for (Expr arg : e.args()) arguments.add(expression(arg, env));
            if (!(callable instanceof FunctionValue function))
                throw new LangException("TYPE", "Not callable");
            return function.call(this, arguments);
        }
        throw new IllegalArgumentException("Unknown expression");
    }

    private static Cell cell(Environment env, Slot slot) {
        for (Environment current = env; current != null; current = current.parent) {
            Cell found = current.cells.get(slot);
            if (found != null) return found;
        }
        throw new IllegalStateException("Resolved slot has no runtime cell");
    }

    private static long number(Object value) {
        if (value instanceof Long n) return n;
        throw new LangException("TYPE", "Expected integer");
    }
}
