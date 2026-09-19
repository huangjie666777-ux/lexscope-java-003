import lexscope.*;
import java.util.List;
import static lexscope.Ast.*;

public class Demo {
    public static void main(String[] args) {
        // 1. Calling a function before its Fun statement: functions are
        //    initialized when their scope is entered.
        new Engine().execute(List.of(
            new Emit(new Call(new Var("answer"), List.of())),
            new Fun("answer", List.of(), List.of(new Return(new Num(42))))),
            System.out::println);

        // 2. Independent counter closures: each outer call captures fresh
        //    cells, so the counters do not interfere.
        new Engine().execute(List.of(
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
            System.out::println);

        // 3. A Let cell is temporarily unreadable: reading it before its
        //    statement ran throws UNINITIALIZED instead of falling back to
        //    an outer binding.
        try {
            new Engine().execute(List.of(
                new Let("x", new Num(1)),
                new Block(List.of(
                    new Emit(new Var("x")),
                    new Let("x", new Num(2))))),
                System.out::println);
        } catch (LangException e) {
            System.out.println(e.code() + ": " + e.getMessage());
        }
    }
}
