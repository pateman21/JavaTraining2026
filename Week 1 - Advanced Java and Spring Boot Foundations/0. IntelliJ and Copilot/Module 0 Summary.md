# Java Full Stack Development: Module 0 Topic Summary

## Module 1: IntelliJ Setup and Navigation

- **IntelliJ IDEA overview**: polyglot IDE with native Java support operating on a fully resolved semantic model, contrasted with VS Code's LSP-based approach
- **Project setup**: two-JVM model (IDE JVM vs. project JVM), Maven project structure, `pom.xml` as the single source of truth, Scratches and Consoles
- **IDE configuration**: Maven auto-import, UTF-8 file encoding enforcement, IDE heap sizing for large Spring Boot projects, Run Configurations (Temporary, Permanent, Shared)
- **Code style toolchain**: EditorConfig (cross-editor baseline), IntelliJ Code Style Engine (IDE-specific formatting), Checkstyle (build-time enforcement)
- **Checkstyle**: cyclomatic complexity limits, suppressions file for generated code, legacy code, test code, and framework-constrained classes
- **Code inspections**: continuous static analysis via IntelliJ's semantic model, three severity levels, Alt+Enter quick-fix workflow, Spring-specific inspections in Ultimate, contrast with SAST tools such as Checkmarx
- **Refactoring**: definition and purpose, four code smells (duplication, long methods, poor naming, tight coupling), vocabulary of refactoring operations (Extract Method, Rename, Move, Introduce Parameter Object, Replace Conditional with Polymorphism), semantic safety guarantees in IntelliJ
- **Codebase navigation**: three navigation modes: name-based (`Ctrl+N`, `Shift+Shift`), structure-based (`Ctrl+F12`, `Alt+F7`), and relationship-based (`Ctrl+B`, `Ctrl+Alt+B`, `Ctrl+H`, `Ctrl+Alt+H`)

---

## Module 2: IntelliJ Debugging and Testing

- **Debugger fundamentals**: Java Debug Wire Protocol (JDWP), launch mode vs. remote attach mode, source mapping without special compiler flags
- **Breakpoint types**: line, method, field watchpoint, exception; conditional breakpoints; logging breakpoints (non-suspending)
- **Debug tool window**: Frames panel (call stack navigation), Variables panel, Watches, inline value rendering, Quick Evaluate on hover
- **Stepping controls**: Step Over, Step Into, Force Step Into (bypasses JDK and library filters), Step Out
- **Expression evaluation and state modification**: Alt+F8 Evaluate Expression, Set Value on variables while suspended, Drop Frame to re-execute a method
- **Remote debugging**: JDWP agent flag, Remote JVM Debug Run Configuration, common use cases including Docker containers and pre-production environments
- **Run Configurations**: Temporary vs. Permanent vs. Shared configurations, Spring Boot Run Configuration fields (main class, active profiles, VM options, environment variables, working directory), Spring property precedence order, Compound configurations for multi-service startup, Before Launch actions
- **Test discovery and execution**: `src/test/java` as Test Sources Root, run options from single method to full module, `Ctrl+Shift+F10` shortcut
- **Test results and debugging**: Test Run tool window, failure diff view, clickable stack traces, debugging tests with breakpoints in both test and production code
- **Code coverage**: Run with Coverage, IntelliJ built-in mode vs. JaCoCo mode, gutter highlighting, coverage tool window, HTML report export

---

## Module 3: Working with GitHub Copilot

- **Copilot integration in IntelliJ**: first-party JetBrains plugin, OAuth authentication via GitHub, two primary modes: inline completions and Copilot Chat panel
- **Copilot Chat**: slash commands (`/explain`, `/fix`, `/tests`, `/doc`), `#` file and symbol references, conversational prompt-response model, dockable panel
- **How Copilot works**: next-token prediction engine trained on public GitHub repositories, not a compiler or reasoning system; quality of output correlates with training data coverage
- **Training data coverage**: strong for standard Spring Boot patterns (REST controllers, JPA repositories, service layers, unit test structure); weaker for custom security filters, Oracle-specific SQL, Kafka retry semantics, and organisation-specific frameworks
- **Context window**: measured in tokens (roughly 3-4 characters each), includes current file, cursor position, recently opened files, and selected code; does not include `application.yml`, database schema, or undocumented business rules
- **Inline completions**: accept with Tab, word-by-word with `Ctrl+Right`, cycle alternatives with `Alt+]` / `Alt+[`, dismiss with Escape; treat every suggestion as a code review, not a guaranteed-correct completion
- **Comment-driven code**: writing a descriptive comment as a specification before implementation; useful for query methods, algorithm implementations, and utility methods with clear contracts
- **Writing and validating tests**: Chat `/tests` command and inline approaches; risk of tests that pass without verifying the correct behaviour because they are derived from the implementation rather than the specification
- **Code review and validation**: targeted Chat prompts for edge cases, thread safety, resource leaks, and security implications; Copilot as a supplement to IntelliJ inspections, Checkstyle, and Checkmarx, not a replacement
- **Prompt engineering principles**: be specific about intent; provide context explicitly; use code itself as context (names, signatures, Javadoc); iterate rather than accept and move on; validate all output; avoid sensitive data in prompts (credentials, PII, internal hostnames)
- **Hallucination**: Copilot can generate references to non-existent methods or incorrect API signatures; the compiler and IntelliJ inspections are the first line of defence
- **Quality gate model**: formal gates (IntelliJ inspections, Checkstyle, JUnit coverage, Checkmarx SAST) are deterministic, enforceable, and auditable; Copilot is probabilistic and advisory; Copilot accelerates development but does not replace the quality gate system
