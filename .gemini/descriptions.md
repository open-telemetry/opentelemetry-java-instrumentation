# metadata.yaml Description Guidelines

When investigating what a module does, you can find the code within the directory given. There should be a subdirectory for either a javaagent or library or both.
the gradle files will be located within each submodule:

for example: /Users/jay/code/projects/opentelemetry-java-instrumentation/instrumentation/apache-httpclient/apache-httpclient-5.2/library/build.gradle.kts

Key guidelines for writing descriptions in `metadata.yaml` files:

1.  **Clarity on Telemetry Generation:**
    *   Explicitly state whether the instrumentation generates new telemetry (spans, metrics, logs) on its own.
    *   If it *doesn't* generate new telemetry, clearly explain that it *augments* or *enriches* existing telemetry produced by other instrumentations (e.g., by adding attributes or ensuring context propagation).

2.  **Avoid Implementation Details:**
    *   Do not include specific method names, class names, or other low-level implementation details in the description. Focus on the *what* and *why* of the instrumentation, not the *how*.

3.  **Adhere to Style and Formatting:**
    *   Respect line length limits (e.g., 100 characters as per `.editorconfig`).
    *   Use appropriate line breaks to ensure readability and adherence to the repository's style.

4.  **No Explicit Version Numbers:**
    *   Do not include specific library or framework version numbers in the description. This information should be documented elsewhere (e.g., in `muzzle` rules or other documentation). The description should be more general about the instrumentation's purpose.

# Generating test telemetry

Sometimes in order to get more information about the telemetry emitted by an instrumentation, we need to run tests that exercise the code paths that generate the telemetry. This is especially true for spans and metrics.

You can generate this telemetry by running the tests in the instrumentation module. To do this, you need to set the `collectMetadata` property to `true` in your Gradle command. For example:

```bash
./gradlew :instrumentation-module-name:test -PcollectMetadata=true
```
