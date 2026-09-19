import lexscope.*;
import java.util.*;
import static lexscope.Ast.*;

public class SemanticsTest {
    static int passed;

    static void check(List<Stmt> program, Long... expected) {
        List<Long> out = new ArrayList<>();
        new Engine().execute(program, out::add);
        if (!out.equals(List.of(expected))) throw new AssertionError(out);
        passed++;
    }

    static void fails(String code, List<Stmt> program) {
        List<Long> out = new ArrayList<>();
        try {
            new Engine().execute(program, out::add);
        } catch (LangException e) {
            if (!e.code().equals(code)) throw new AssertionError(code + " != " + e.code());
            if (code.equals("RESOLVE") && !out.isEmpty()) throw new AssertionError("output before RESOLVE");
            passed++;
            return;
        }
        throw new AssertionError("expected " + code);
    }

    public static void main(String[] args) {
        // Static errors anywhere, no output before failure.
        fails("RESOLVE", List.of(new Emit(new Num(1)), new Emit(new Var("ghost"))));
        fails("RESOLVE", List.of(new If(new Num(1),
            new Block(List.of(new Emit(new Num(1)))),
            new Block(List.of(new Emit(new Var("ghost")))))));
        fails("RESOLVE", List.of(new Fun("f", List.of(), List.of(new Emit(new Var("ghost")))),
            new Emit(new Num(1))));
        fails("RESOLVE", List.of(new Let("x", new Num(1)), new Let("x", new Num(2))));
        fails("RESOLVE", List.of(new Fun("f", List.of("a", "a"), List.of())));
        fails("RESOLVE", List.of(new Fun("f", List.of("a"), List.of(new Let("a", new Num(1))))));
        fails("RESOLVE", List.of(new Return(new Num(1))));
        fails("RESOLVE", List.of(new Assign("ghost", new Num(1))));

        // Forward references: functions callable before their Fun statement; mutual recursion.
        check(List.of(
            new Emit(new Call(new Var("f"), List.of())),
            new Fun("f", List.of(), List.of(new Return(new Num(42))))), 42L);
        check(List.of(
            new Fun("even", List.of("n"), List.of(new If(new Var("n"),
                new Block(List.of(new Return(new Call(new Var("odd"),
                    List.of(new Binary(new Var("n"), Op.SUB, new Num(1))))))),
                new Block(List.of(new Return(new Num(1))))))),
            new Fun("odd", List.of("n"), List.of(new If(new Var("n"),
                new Block(List.of(new Return(new Call(new Var("even"),
                    List.of(new Binary(new Var("n"), Op.SUB, new Num(1))))))),
                new Block(List.of(new Return(new Num(0))))))),
            new Emit(new Call(new Var("even"), List.of(new Num(10)))),
            new Emit(new Call(new Var("odd"), List.of(new Num(8))))), 1L, 0L);

        // UNINITIALIZED: self-reference, early call, assign before let, no outer fallback.
        fails("UNINITIALIZED", List.of(new Let("x", new Var("x"))));
        fails("UNINITIALIZED", List.of(
            new Fun("f", List.of(), List.of(new Return(new Var("x")))),
            new Eval(new Call(new Var("f"), List.of())),
            new Let("x", new Num(1))));
        fails("UNINITIALIZED", List.of(new Assign("x", new Num(1)), new Let("x", new Num(2))));
        fails("UNINITIALIZED", List.of(new Let("x", new Num(9)),
            new Block(List.of(new Emit(new Var("x")), new Let("x", new Num(1))))));
        // Uncalled function may reference a later Let; fine once initialized.
        check(List.of(
            new Fun("f", List.of(), List.of(new Return(new Var("x")))),
            new Let("x", new Num(5)),
            new Emit(new Call(new Var("f"), List.of()))), 5L);

        // Closures capture cells: shared within one call, independent across calls.
        check(List.of(
            new Fun("make", List.of(), List.of(
                new Let("n", new Num(0)),
                new Fun("inc", List.of(), List.of(
                    new Assign("n", new Binary(new Var("n"), Op.ADD, new Num(1))),
                    new Return(new Var("n")))),
                new Return(new Var("inc")))),
            new Let("a", new Call(new Var("make"), List.of())),
            new Let("b", new Call(new Var("make"), List.of())),
            new Emit(new Call(new Var("a"), List.of())),
            new Emit(new Call(new Var("a"), List.of())),
            new Emit(new Call(new Var("b"), List.of()))), 1L, 2L, 1L);

        // Cross-block assignment to an outer cell; return unwinds nested blocks only.
        check(List.of(new Let("x", new Num(1)),
            new Block(List.of(new Block(List.of(new Assign("x", new Num(8)))))),
            new Emit(new Var("x"))), 8L);
        check(List.of(
            new Fun("f", List.of(), List.of(
                new Block(List.of(new If(new Num(1),
                    new Block(List.of(new Return(new Num(3)))),
                    new Block(List.of())))),
                new Emit(new Num(99)))),
            new Emit(new Call(new Var("f"), List.of())),
            new Emit(new Num(4))), 3L, 4L);
        // Falling off the end returns zero.
        check(List.of(new Fun("f", List.of(), List.of(new Eval(new Num(1)))),
            new Emit(new Call(new Var("f"), List.of()))), 0L);

        // Structurally equal but distinct nodes resolve separately.
        check(List.of(
            new Let("x", new Num(1)),
            new Block(List.of(new Let("x", new Num(2)), new Emit(new Var("x")))),
            new Block(List.of(new Let("x", new Num(3)), new Emit(new Var("x")))),
            new Emit(new Var("x"))), 2L, 3L, 1L);

        // Engine reuse does not leak bindings or resolution.
        Engine engine = new Engine();
        List<Long> out = new ArrayList<>();
        engine.execute(List.of(new Let("x", new Num(1)), new Emit(new Var("x"))), out::add);
        try {
            engine.execute(List.of(new Emit(new Var("x"))), out::add);
            throw new AssertionError("expected RESOLVE");
        } catch (LangException e) {
            if (!e.code().equals("RESOLVE")) throw new AssertionError(e.code());
        }
        engine.execute(List.of(new Let("x", new Num(2)), new Emit(new Var("x"))), out::add);
        if (!out.equals(List.of(1L, 2L))) throw new AssertionError(out);
        passed++;

        // TYPE error when calling a non-function.
        try {
            new Engine().execute(List.of(new Let("f", new Num(1)),
                new Eval(new Call(new Var("f"), List.of(new Num(0), new Num(0))))), v -> {});
            throw new AssertionError("expected TYPE");
        } catch (LangException e) {
            if (!e.code().equals("TYPE")) throw new AssertionError(e.code());
        }
        passed++;

        System.out.println("SemanticsTest: " + passed + " passed");
    }
}
