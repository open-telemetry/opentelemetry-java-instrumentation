Use Java 25 as the default compilation and test toolchain, matching `.java-version`.

The latest-deps job inherits each project's toolchain instead of forcing a test Java version. Projects capped below Java 25 continue running on supported JDKs, while projects that compile newer dependencies use the required JDK. Explicit test Java versions in the regular CI matrix keep their existing behavior.

Includes Java 25 compatibility updates for affected instrumentation builds and module-opening error handling.
