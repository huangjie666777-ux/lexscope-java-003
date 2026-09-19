import lexscope.*;
import java.util.*;
import static lexscope.Ast.*;

public class ScopeTest {
    static void check(List<Stmt> program, Long... expected) {
        List<Long> out = new ArrayList<>();
        new Engine().execute(program, out::add);
        if (!out.equals(List.of(expected))) throw new AssertionError(out);
    }

    static void fails(String code, List<Stmt> program) {
        List<Long> out = new ArrayList<>();
        try {
            new Engine().execute(program, out::add);
        } catch (LangException e) {
            if (!e.code().equals(code))
                throw new AssertionError("expected " + code + " got " + e.code());
            return;
        }
        throw new AssertionError("expected " + code + " but run succeeded, output=" + out);
    }

    /** RESOLVE must happen before any output. */
    static void failsQuietly(String code, List<Stmt> program) {
        List<Long> out = new ArrayList<>();
        try {
            new Engine().execute(program, out::add);
        } catch (LangException e) {
            if (!e.code().equals(code))
                throw new AssertionError("expected " + code + " got " + e.code());
            if (!out.isEmpty()) throw new AssertionError("output before RESOLVE: " + out);
            return;
        }
        throw new AssertionError("expected " + code);
    }

    public static void main(String[] args) {
        // Call before the Fun statement; mutual recursion in one scope.
        check(List.of(
            new Emit(new Call(new Var("even"), List.of(new Num(10)))),
            new Fun("even", List.of("n"), List.of(
                new If(new Var("n"),
                    new Block(List.of(new Return(new Call(new Var("odd"),
                        List.of(new Binary(new Var("n"), Op.SUB, new Num(1))))))),
                    new Block(List.of(new Return(new Num(1))))))),
            new Fun("odd", List.of("n"), List.of(
                new If(new Var("n"),
                    new Block(List.of(new Return(new Call(new Var("even"),
                        List.of(new Binary(new Var("n"), Op.SUB, new Num(1))))))),
                    new Block(List.of(new Return(new Num(0)))))))),
            1L);

        // Independent counter closures from separate outer calls.
        check(List.of(
            new Fun("counter", List.of(), List.of(
                new Let("n", new Num(0)),
                new Fun("inc", List.of(), List.of(
                    new Assign("n", new Binary(new Var("n"), Op.ADD, new Num(1))),
                    new Return(new Var("n")))),
                new Return(new Var("inc")))),
            new Let("a", new Call(new Var("counter"), List.of())),
            new Let("b", new Call(new Var("counter"), List.of())),
            new Emit(new Call(new Var("a"), List.of())),
            new Emit(new Call(new Var("a"), List.of())),
            new Emit(new Call(new Var("b"), List.of()))),
            1L, 2L, 1L);

        // Closures created in one outer call share the captured cell.
        check(List.of(
            new Fun("pair", List.of(), List.of(
                new Let("n", new Num(0)),
                new Fun("bump", List.of(), List.of(
                    new Assign("n", new Binary(new Var("n"), Op.ADD, new Num(1))))),
                new Fun("read", List.of(), List.of(new Return(new Var("n")))),
                new Eval(new Call(new Var("bump"), List.of())),
                new Eval(new Call(new Var("bump"), List.of())),
                new Return(new Var("read")))),
            new Let("r", new Call(new Var("pair"), List.of())),
            new Emit(new Call(new Var("r"), List.of()))),
            2L);

        // Closure escapes its defining Block and stays usable.
        check(List.of(
            new Let("f", new Num(0)),
            new Block(List.of(
                new Let("x", new Num(41)),
                new Fun("g", List.of(), List.of(
                    new Return(new Binary(new Var("x"), Op.ADD, new Num(1))))),
                new Assign("f", new Var("g")))),
            new Emit(new Call(new Var("f"), List.of()))),
            42L);

        // Function referring to a later Let: error only if called too early.
        fails("UNINITIALIZED", List.of(
            new Fun("get", List.of(), List.of(new Return(new Var("v")))),
            new Emit(new Call(new Var("get"), List.of())),
            new Let("v", new Num(3))));
        check(List.of(
            new Fun("get", List.of(), List.of(new Return(new Var("v")))),
            new Let("v", new Num(3)),
            new Emit(new Call(new Var("get"), List.of()))),
            3L);

        // Self-reference in a Let initializer is uninitialized, no outer fallback.
        fails("UNINITIALIZED", List.of(
            new Let("x", new Num(9)),
            new Block(List.of(new Let("x", new Var("x"))))));
        fails("UNINITIALIZED", List.of(new Let("x", new Var("x"))));

        // Assignment before the Let statement is uninitialized too.
        fails("UNINITIALIZED", List.of(
            new Fun("set", List.of(), List.of(new Assign("v", new Num(1)))),
            new Eval(new Call(new Var("set"), List.of())),
            new Let("v", new Num(0))));

        // Static errors anywhere fail the whole run before output.
        failsQuietly("RESOLVE", List.of(
            new Emit(new Num(1)),
            new Fun("f", List.of(), List.of(new Eval(new Var("nope"))))));
        failsQuietly("RESOLVE", List.of(
            new Emit(new Num(1)),
            new If(new Num(1),
                new Block(List.of()),
                new Block(List.of(new Eval(new Var("nope")))))));
        failsQuietly("RESOLVE", List.of(
            new Let("x", new Num(1)),
            new Let("x", new Num(2))));
        failsQuietly("RESOLVE", List.of(
            new Fun("f", List.of("p"), List.of(new Let("p", new Num(1))))));
        failsQuietly("RESOLVE", List.of(new Return(new Num(1))));
        failsQuietly("RESOLVE", List.of(
            new Block(List.of(new Return(new Num(1))))));

        // Structurally equal but distinct Var nodes resolve separately.
        check(List.of(
            new Let("x", new Num(1)),
            new Block(List.of(
                new Let("x", new Num(2)),
                new Emit(new Var("x")))),
            new Emit(new Var("x"))),
            2L, 1L);

        // Return unwinds nested Block/If and exits only the current function.
        check(List.of(
            new Fun("f", List.of(), List.of(
                new Block(List.of(new If(new Num(1),
                    new Block(List.of(new Block(List.of(new Return(new Num(7)))))),
                    new Block(List.of())))),
                new Emit(new Num(99)))),
            new Emit(new Call(new Var("f"), List.of())),
            new Emit(new Num(5))),
            7L, 5L);

        // Falling off the end returns zero; function bindings are mutable.
        check(List.of(
            new Fun("f", List.of(), List.of()),
            new Emit(new Call(new Var("f"), List.of())),
            new Fun("g", List.of(), List.of(new Return(new Num(4)))),
            new Assign("f", new Var("g")),
            new Emit(new Call(new Var("f"), List.of()))),
            0L, 4L);

        // TYPE / ARITY still work; arguments evaluate before the failure.
        fails("TYPE", List.of(new Let("x", new Num(1)), new Eval(new Call(new Var("x"), List.of()))));
        fails("ARITY", List.of(
            new Fun("f", List.of("a"), List.of()),
            new Eval(new Call(new Var("f"), List.of()))));
        {
            List<Long> out = new ArrayList<>();
            try {
                new Engine().execute(List.of(
                    new Fun("side", List.of(), List.of(new Emit(new Num(8)), new Return(new Num(0)))),
                    new Let("n", new Num(1)),
                    new Eval(new Call(new Var("n"), List.of(new Call(new Var("side"), List.of()))))),
                    out::add);
                throw new AssertionError("expected TYPE");
            } catch (LangException e) {
                if (!e.code().equals("TYPE")) throw new AssertionError(e.code());
            }
            if (!out.equals(List.of(8L))) throw new AssertionError(out);
        }

        // Reusing one Engine leaks nothing between runs.
        Engine engine = new Engine();
        List<Long> first = new ArrayList<>();
        engine.execute(List.of(new Let("x", new Num(1)), new Emit(new Var("x"))), first::add);
        List<Long> second = new ArrayList<>();
        try {
            engine.execute(List.of(new Emit(new Var("x"))), second::add);
            throw new AssertionError("second run should fail");
        } catch (LangException e) {
            if (!e.code().equals("RESOLVE")) throw new AssertionError(e.code());
        }
        if (!first.equals(List.of(1L)) || !second.isEmpty()) throw new AssertionError();

        System.out.println("ScopeTest: all passed");
    }
}
