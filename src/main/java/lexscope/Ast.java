package lexscope;

import java.util.List;

/** Immutable host-built syntax tree; no parser is required. */
public final class Ast {
    private Ast() {}
    public sealed interface Expr permits Num, Var, Binary, Call {}
    public sealed interface Stmt permits Let, Assign, Fun, Block, If, Return, Emit, Eval {}
    public record Num(long value) implements Expr {}
    public record Var(String name) implements Expr {}
    public enum Op { ADD, SUB, MUL, LE }
    public record Binary(Expr left, Op op, Expr right) implements Expr {}
    public record Call(Expr callee, List<Expr> args) implements Expr {
        public Call { args = List.copyOf(args); }
    }
    public record Let(String name, Expr initializer) implements Stmt {}
    public record Assign(String name, Expr value) implements Stmt {}
    public record Fun(String name, List<String> params, List<Stmt> body) implements Stmt {
        public Fun { params = List.copyOf(params); body = List.copyOf(body); }
    }
    public record Block(List<Stmt> body) implements Stmt {
        public Block { body = List.copyOf(body); }
    }
    public record If(Expr condition, Block yes, Block no) implements Stmt {}
    public record Return(Expr value) implements Stmt {}
    public record Emit(Expr value) implements Stmt {}
    public record Eval(Expr value) implements Stmt {}
}
