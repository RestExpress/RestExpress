# Repository Guidelines

## Project Structure & Module Organization
- `pom.xml` is the parent Maven build for a multi-module project.
- `common/` contains shared utilities and query helpers in `common/src/main/java`.
- `core/` contains the main RestExpress server/runtime in `core/src/main/java`.
- Tests live alongside each module in `*/src/test/java`, with test resources under `core/src/test/resources`.
- `docs/` and `images/` hold reference material and assets; they are not part of the build.
- `core/src/scripts/` includes helper shell scripts for local use.

## Build, Test, and Development Commands
- `mvn test` runs the full test suite across all modules.
- `mvn -pl common test` runs only the common module tests.
- `mvn -pl core -am test` runs core tests and builds dependencies.
- `mvn -DskipTests package` builds jars without running tests.
- Requires JDK 21 (see `pom.xml` `jdk.version`).

## Coding Style & Naming Conventions
- Java source uses tabs for indentation with braces on their own line.
- Packages follow `org.restexpress.*`; class names are PascalCase and methods camelCase.
- Test classes use the `*Test` suffix (e.g., `QueryFilterTest`).
- Keep public API changes documented in `README.md` change history when applicable.

## Testing Guidelines
- Test framework is JUnit 4 (declared in the parent `pom.xml`).
- Place unit tests under `*/src/test/java` and test data under `*/src/test/resources`.
- Prefer focused unit tests for new behavior; update existing tests when changing behavior.

## Commit & Pull Request Guidelines
- Follow the existing commit style: short, sentence-case summaries (e.g., “Upgraded dependencies.”).
- PRs should describe the change, include relevant issue links, and note test coverage or commands run.
- Include any breaking API notes in the PR description and update `README.md` if needed.

## Configuration Tips
- Environment properties for tests are under `core/src/test/resources/config/dev/environment.properties`.
- If adding new configuration, document the keys and defaults in `README.md` or module docs.
