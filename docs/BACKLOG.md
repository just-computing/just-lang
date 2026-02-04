# Backlog

## Compiler Frontend

- Improve `Lexer`/`Parser` architecture to reduce raw string-literal token/operator handling and move to a higher-level, typed operator/token model.
  - Goal: simplify grammar evolution, reduce parser branching complexity, and improve maintainability.
  - Scope hint: introduce token/operator enums and parser helpers so syntax changes are localized.
