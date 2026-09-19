package lexscope;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static lexscope.Ast.*;

/**
 * AST evaluator. Each execute() run first resolves the whole program
 * statically (no output on resolution failure), then interprets it against
 * fresh runtime environments. No state leaks between runs of the same Engine.
 */
public final class Engine {
    private Consumer<Long> output;
    private Resolver resolver;

    public void execute(List<Stmt> program, Consumer<Long> output) {
        Resolver resolver = new Resolver();
        Resolver.Scope programScope = resolver.resolveProgram(program);
        this.output = output;
        this.resolver = resolver;
        try {
            statements(program, new Environment(null, programScope));
        } finally {
            this.output = null;
            this.resolver = null;
        }
    }

    Resolver.Ref ref(Object node) {
        return resolver.refs.get(node);
    }

    Resolver.Scope scopeOf(Stmt statement) {
        return resolver.scopes.get(statement);
    }

    void statements(List<Stmt> body, Environment env) {
        for (Stmt statement : body) statement(statement, env);
    }

    private void statement(Stmt statement, Environment env) {
        if (statement instanceof Let s) {
            Object value = expression(s.initializer(), env);
            env.initialize(ref(s), value);
        } else if (statement instanceof Assign s) {
            Object value = expression(s.value(), env);
            env.assign(ref(s), value);
        } else if (statement instanceof Fun) {
            // Function cells were initialized when the scope was entered.
        } else if (statement instanceof Block s) {
            statements(s.body(), new Environment(env, scopeOf(s)));
        } else if (statement instanceof If s) {
            Block branch = number(expression(s.condition(), env)) != 0 ? s.yes() : s.no();
            statements(branch.body(), new Environment(env, scopeOf(branch)));
        } else if (statement instanceof Return s) {
            throw new ReturnSignal(expression(s.value(), env));
        } else if (statement instanceof Emit s) {
            output.accept(number(expression(s.value(), env)));
        } else if (statement instanceof Eval s) {
            expression(s.value(), env);
        } else {
            throw new IllegalArgumentException("Unknown statement");
        }
    }

    private Object expression(Expr expression, Environment env) {
        if (expression instanceof Num e) return e.value();
        if (expression instanceof Var e) return env.get(ref(e));
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

    private static long number(Object value) {
        if (value instanceof Long n) return n;
        throw new LangException("TYPE", "Expected integer");
    }
}
