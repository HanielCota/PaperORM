# PaperORM Rules & Guidelines

These rules dictate how any AI agent should interact with and modify the PaperORM project.

## 1. Architectural Philosophy
* **Lightweight First**: PaperORM is designed to be a lightweight, embedded ORM for PaperSpigot plugins. Do not introduce heavy dependencies (e.g., ByteBuddy, CGLib, Hibernate core modules) without explicit user permission.
* **Modern Java**: The codebase targets Java 21+. Use modern language features like `record` classes, `sealed` hierarchies, pattern matching, and enhanced switch statements wherever applicable.
* **Performance**: Avoid classic Reflection (`Method.invoke`, `Field.get/set`) when performance is critical. Use `MethodHandles` and `VarHandle` to keep runtime execution near-native.

## 2. SOLID Principles
* Maintain **Dependency Inversion**: Ensure high-level modules (e.g., repositories) depend on interfaces (like `RepositoryContext`, `SessionCacheProvider`), not directly on concrete classes (like `OrmContext`, `OrmSession`).
* Follow **Interface Segregation**: Keep interfaces small and domain-specific (e.g., separating `SchemaDialect` from `QueryDialect`).

## 3. Testing
* Ensure 100% test passing before proposing any commits.
* Run `./gradlew spotlessApply` and `./gradlew test` to enforce formatting and verify stability after any code changes.
* Null-safety is paramount: Always double-check edge cases involving primitive unboxing and Null Pointer Exceptions, especially within the Query Builder (`Spec.java`) and `EntityMapper`.

## 4. Git & Commits
* **Language**: All commit messages MUST be written in English.
* **Format**: Commits should be descriptive, detailing *what* changed and *why*.
