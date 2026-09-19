package lexscope;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static lexscope.Ast.*;

/**
 * Static resolution pass. Before any evaluation or output, the whole AST is
 * validated: duplicate declarations in one scope, undefined Var/Assign names
 * and Return outside a function all fail here with a RESOLVE error. Each
 * Var/Assign occurrence is bound, by node identity, to a lexical slot in a
 * static scope, so structurally equal but distinct nodes resolve separately.
 */
final class Resolver {
    /** A static scope: program, explicit Block, or function (params + body). */
    static final class Scope {
        final Scope parent;
        final Map<String, Integer> slots = new LinkedHashMap<>();
        /** Direct declarations by slot; null entries are function parameters. */
        final List<Stmt> declarations = new ArrayList<>();

        Scope(Scope parent) {
            this.parent = parent;
        }

        int declare(String name, Stmt node) {
            if (slots.containsKey(name))
                throw new LangException("RESOLVE", "Duplicate declaration: " + name);
            int slot = slots.size();
            slots.put(name, slot);
            declarations.add(node);
            return slot;
        }

        int size() {
            return slots.size();
        }
    }

    /** Static binding of one reference to a slot in a specific scope. */
    static final class Ref {
        final Scope scope;
        final int slot;

        Ref(Scope scope, int slot) {
            this.scope = scope;
            this.slot = slot;
        }
    }

    /** Block/Fun statement -> the static scope it introduces. */
    final IdentityHashMap<Stmt, Scope> scopes = new IdentityHashMap<>();
    /** Var/Assign/Let node -> its resolved slot (Let: its own cell). */
    final IdentityHashMap<Object, Ref> refs = new IdentityHashMap<>();

    Scope resolveProgram(List<Stmt> body) {
        Scope scope = new Scope(null);
        declareAll(scope, body);
        for (Stmt statement : body) resolveStatement(statement, scope, false);
        return scope;
    }

    private void declareAll(Scope scope, List<Stmt> body) {
        for (Stmt statement : body) {
            if (statement instanceof Let let) {
                refs.put(let, new Ref(scope, scope.declare(let.name(), let)));
            } else if (statement instanceof Fun fun) {
                scope.declare(fun.name(), fun);
            }
        }
    }

    private Ref lookup(Scope scope, String name) {
        for (Scope current = scope; current != null; current = current.parent) {
            Integer slot = current.slots.get(name);
            if (slot != null) return new Ref(current, slot);
        }
        throw new LangException("RESOLVE", "Undefined name: " + name);
    }

    private void resolveStatement(Stmt statement, Scope scope, boolean inFunction) {
        if (statement instanceof Let let) {
            resolveExpression(let.initializer(), scope, inFunction);
        } else if (statement instanceof Assign assign) {
            resolveExpression(assign.value(), scope, inFunction);
            refs.put(assign, lookup(scope, assign.name()));
        } else if (statement instanceof Fun fun) {
            Scope inner = new Scope(scope);
            for (String param : fun.params()) inner.declare(param, null);
            declareAll(inner, fun.body());
            scopes.put(fun, inner);
            for (Stmt body : fun.body()) resolveStatement(body, inner, true);
        } else if (statement instanceof Block block) {
            Scope inner = new Scope(scope);
            declareAll(inner, block.body());
            scopes.put(block, inner);
            for (Stmt body : block.body()) resolveStatement(body, inner, inFunction);
        } else if (statement instanceof If conditional) {
            resolveExpression(conditional.condition(), scope, inFunction);
            resolveStatement(conditional.yes(), scope, inFunction);
            resolveStatement(conditional.no(), scope, inFunction);
        } else if (statement instanceof Return ret) {
            if (!inFunction)
                throw new LangException("RESOLVE", "Return outside a function");
            resolveExpression(ret.value(), scope, inFunction);
        } else if (statement instanceof Emit emit) {
            resolveExpression(emit.value(), scope, inFunction);
        } else if (statement instanceof Eval eval) {
            resolveExpression(eval.value(), scope, inFunction);
        } else {
            throw new IllegalArgumentException("Unknown statement");
        }
    }

    private void resolveExpression(Expr expression, Scope scope, boolean inFunction) {
        if (expression instanceof Num) {
            return;
        } else if (expression instanceof Var var) {
            refs.put(var, lookup(scope, var.name()));
        } else if (expression instanceof Binary binary) {
            resolveExpression(binary.left(), scope, inFunction);
            resolveExpression(binary.right(), scope, inFunction);
        } else if (expression instanceof Call call) {
            resolveExpression(call.callee(), scope, inFunction);
            for (Expr argument : call.args()) resolveExpression(argument, scope, inFunction);
        } else {
            throw new IllegalArgumentException("Unknown expression");
        }
    }
}
