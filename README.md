# lexscope

Existing Java 17 AST interpreter with separate syntax, environment, functions,
execution and error modules. It currently supports straightforward examples but
has defects in binding, function creation and closure lifetime. This project is
host-embedded: the host constructs immutable Ast records; no text parser or CLI
is needed. The following is the target contract, not a claim that it already works.

## Host API and inputs

`new Engine().execute(List<Ast.Stmt> program, Consumer<Long> output)` executes an
independent program. A host callback consumes emitted integer values. Callback
exceptions propagate unchanged. Do not catch them as language control flow.
The host reuses Engine and AST objects between sequential runs. Each run starts
with fresh runtime bindings and fresh resolution state; a failed run must not
pollute a later run. No concurrent/reentrant execute or rollback of emitted output
is required. Do not modify the supplied records or lists.

Inputs are well-formed finite trees: nonnull fields, valid nonempty identifiers,
known operators, and each node object occurs in only one position in a given
tree. Distinct record objects may be structurally equal and must still resolve
according to their separate occurrences. No validation of those input premises
is required. Empty lists/blocks/parameter lists are valid.

## Scope and resolution (before execution)

A program is one scope. Each explicit Block creates a child scope. Each function
call creates a child scope of that function's DEFINITION environment. Parameters
and the function body's direct declarations share one scope (no extra body scope).
The Block branches of If are ordinary explicit scopes.

All direct Let and Fun names in a scope are declared throughout that whole scope,
including before their textual declaration. Resolve all Var and Assign targets
to the nearest enclosing declaration. A later local declaration shadows an outer
name even at earlier source positions. Declarations in descendant blocks do not
leak into a parent or sibling. Function bodies are resolved in their definition
context, not in caller scopes.

Before ANY evaluation or output, validate the entire AST, including unchosen If
branches and uncalled function bodies. Undefined Var/Assign names, duplicate
Let/Fun/parameter names in the same scope, and Return outside any function throw
`new LangException("RESOLVE", message)`. Shadowing in a nested Block is legal.
No diagnostic wording or ordering among multiple static errors is specified.

## Binding lifecycle and functions

On entering a scope, allocate fresh cells for all its direct declarations.
Initialize function cells immediately, before executing any statements, so calls
before Fun statements, self-recursion and mutual recursion work. A Fun statement
has no second initialization effect. Function bindings are mutable just like Let
bindings and can be read, passed, returned or assigned.

Let cells remain UNINITIALIZED until that Let statement evaluates its initializer
successfully and then stores the result. This includes self-reference in an
initializer, reads from a function called too early, and assignment before the Let
statement. Accessing or assigning an uninitialized cell throws
`LangException("UNINITIALIZED", ...)`; never fall back to an outer binding.
Do not reject a reference merely because the declaration is later: an uncalled
function can refer to a later Let and be called successfully after it initializes.
For Assign, evaluate its RHS first, then check/write the target cell.

Functions capture CELLS in their definition environment, not snapshots of values.
An escaped closure stays usable after the defining call/block returns. Closures
created in one call share captured cells; different outer calls get independent
cells. Return unwinds all nested Block/If execution up to the current function
only. A function falling off its end returns integer zero. Parameters are initialized
from arguments when the function is entered.

## Values and evaluation order

Values are signed Java long integers or opaque function values. Num constructs an
integer. Binary evaluates left first and checks it is an integer, then evaluates
and checks right; ADD/SUB/MUL use normal Java long wraparound, LE returns 1 or 0.
If checks an integer condition; zero is false and any other integer true. Emit
checks an integer and passes it to the host callback; Eval discards any value.

Call evaluates callee, then ALL arguments left-to-right, then checks that the
callee is a function (`TYPE` if not) and the argument count (`ARITY` if wrong).
Arguments are evaluated even when the call subsequently fails TYPE/ARITY.
Using a function where an integer is required throws TYPE. Runtime language
exceptions use LangException; `.code()` returns RESOLVE, UNINITIALIZED, TYPE or
ARITY. No static type checking, stack-limit handling or tail-call optimization.

## Example and development

Ast records are in `src/main/java/lexscope/Ast.java`. Engine coordinates execution,
Environment stores bindings, FunctionValue represents functions and ReturnSignal
implements current function exit. Internals may be reorganized inside this package;
preserve the public host interfaces, AST constructors and exception constructor.
Keep `tests/CompatTest.java` unchanged. Add `*Test.java` executable test classes
with public main methods using assertions or explicit checks; test.sh discovers them.
Tests, Demo.java and README may be added/updated. Build scripts may be adjusted
only as necessary for the same two documented offline commands.

## Implementation notes

Execution is split into two phases inside the `lexscope` package:

1. `Resolver` walks the whole AST before anything runs. It builds a static
   scope tree (program, each Block, each function with its parameters) and
   binds every Var/Assign/Let node, by object identity so structurally
   equal nodes at different positions never mix, to a slot in one static
   scope. Duplicate declarations in one scope, undefined names and Return
   outside a function are reported here as RESOLVE, before any output.
2. `Engine` interprets the resolved program. Each scope entry creates an
   `Environment` activation with one mutable cell per slot; function cells
   are filled immediately, Let cells stay UNINITIALIZED until their
   statement stores a value. `FunctionValue` closes over its defining
   activation, so captured cells stay alive and shared after the defining
   scope exits, and each call creates a fresh child of the definition
   environment. `execute` keeps no state between runs.

```sh
bash test.sh
bash demo.sh
```

No external libraries, network, database or services. The initial Demo only covers
arithmetic; extend it to the task's closure and initialization examples.
