import lexscope.*;
import java.util.List;
import static lexscope.Ast.*;

public class Demo {
    public static void main(String[] args) {
        new Engine().execute(List.of(new Let("x", new Num(6)),
            new Emit(new Binary(new Var("x"), Op.MUL, new Num(7)))), System.out::println);
    }
}
