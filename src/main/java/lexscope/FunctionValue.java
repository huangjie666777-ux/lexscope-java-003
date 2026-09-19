package lexscope;

import java.util.List;
import static lexscope.Ast.*;

/**
 * A function value closes over the environment where its Fun was declared.
 * Each call creates a fresh child of that DEFINITION environment, so closures
 * made in one call share cells and different calls never interfere.
 */
final class FunctionValue {
    private final Fun declaration;
    private final Environment definition;

    FunctionValue(Fun declaration, Environment definition) {
        this.declaration = declaration;
        this.definition = definition;
    }

    Object call(Engine engine, List<Object> arguments) {
        if (arguments.size() != declaration.params().size())
            throw new LangException("ARITY", "Wrong argument count");
        Environment local = new Environment(definition, engine.scopeOf(declaration));
        for (int i = 0; i < arguments.size(); i++)
            local.cells[i] = arguments.get(i);
        try {
            engine.statements(declaration.body(), local);
            return 0L;
        } catch (ReturnSignal signal) {
            return signal.value;
        }
    }
}

final class ReturnSignal extends RuntimeException {
    final Object value;
    ReturnSignal(Object value) { super(null, null, false, false); this.value = value; }
}
