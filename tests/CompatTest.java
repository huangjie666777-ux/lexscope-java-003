import lexscope.*;
import java.util.*;
import static lexscope.Ast.*;

public class CompatTest {
    static void check(List<Stmt> program, Long... expected) {
        List<Long> out = new ArrayList<>();
        new Engine().execute(program, out::add);
        if (!out.equals(List.of(expected))) throw new AssertionError(out);
    }
    public static void main(String[] args) {
        check(List.of(new Let("x", new Num(2)), new Assign("x", new Binary(new Var("x"), Op.ADD, new Num(3))), new Emit(new Var("x"))), 5L);
        check(List.of(new Let("x", new Num(1)), new Block(List.of(new Let("x", new Num(7)), new Emit(new Var("x")))), new Emit(new Var("x"))), 7L, 1L);
        check(List.of(new Fun("twice", List.of("n"), List.of(new Return(new Binary(new Var("n"), Op.MUL, new Num(2))))), new Emit(new Call(new Var("twice"), List.of(new Num(9))))), 18L);
        check(List.of(new If(new Num(0), new Block(List.of(new Emit(new Num(1)))), new Block(List.of(new Emit(new Num(2)))))), 2L);
        System.out.println("CompatTest: 4 passed");
    }
}
