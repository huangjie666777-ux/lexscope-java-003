package lexscope;

import java.util.List;
import static lexscope.Ast.*;

/** A closure: function declaration plus its definition environment. */
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
        Environment local = new Environment(definition);
        engine.enterScope(engine.resolution().scopeOf(declaration), local, arguments);
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
