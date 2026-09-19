import lexscope.*;
import java.util.List;
import static lexscope.Ast.*;

public class Demo {
    public static void main(String[] args) {
        // 1. Calling a function before its Fun statement (functions hoist).
        new Engine().execute(List.of(
            new Emit(new Call(new Var("answer"), List.of())),
            new Fun("answer", List.of(), List.of(new Return(new Num(42))))),
            System.out::println);

        // 2. Independent counter closures capturing mutable cells.
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

        // 3. Temporarily unreadable variable: the inner Let shadows the outer x
        //    throughout the whole block, so the early read throws UNINITIALIZED.
        try {
            new Engine().execute(List.of(
                new Let("x", new Num(9)),
                new Block(List.of(
                    new Emit(new Var("x")),
                    new Let("x", new Num(1))))),
                System.out::println);
        } catch (LangException e) {
            System.out.println(e.code() + ": " + e.getMessage());
        }
    }
}
