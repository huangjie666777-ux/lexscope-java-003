package lexscope;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static lexscope.Ast.*;

/** Existing AST evaluator. Resolution currently happens during execution. */
public final class Engine {
    private Consumer<Long> output;

    public void execute(List<Stmt> program, Consumer<Long> output) {
        this.output = output;
        try {
            statements(program, new Environment(null));
        } catch (ReturnSignal signal) {
            throw new LangException("RESOLVE", "Return outside a function");
        }
    }

    void statements(List<Stmt> body, Environment env) {
        for (Stmt statement : body) statement(statement, env);
    }

    private void statement(Stmt statement, Environment env) {
        if (statement instanceof Let s) {
            env.define(s.name(), expression(s.initializer(), env));
        } else if (statement instanceof Assign s) {
            env.assign(s.name(), expression(s.value(), env));
        } else if (statement instanceof Fun s) {
            env.define(s.name(), new FunctionValue(s, env));
        } else if (statement instanceof Block s) {
            statements(s.body(), new Environment(env));
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
        if (expression instanceof Var e) return env.get(e.name());
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
            return function.call(this, arguments, env);
        }
        throw new IllegalArgumentException("Unknown expression");
    }

    private static long number(Object value) {
        if (value instanceof Long n) return n;
        throw new LangException("TYPE", "Expected integer");
    }
}
