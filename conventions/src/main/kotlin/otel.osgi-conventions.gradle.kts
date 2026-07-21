import io.opentelemetry.instrumentation.gradle.OtelJavaExtension

// Generates OSGi bundle metadata in the jar manifest so a published library artifact can be
// consumed directly in OSGi runtimes. OSGi support is opt-in: apply this plugin explicitly only to
// modules that are known to work as OSGi bundles (verified by the :smoke-tests-osgi suites). Do not
// apply it to shadowed javaagent artifacts or to library instrumentations that add classes to the
// instrumented library's own package (split packages don't resolve across OSGi bundle class
// loaders).

plugins {
  `java-library`
  id("biz.aQute.bnd.builder")
}

// Configured via tasks.named (not afterEvaluate): the action runs when the jar task is realized,
// after each module's build script has set the otelJava properties below.
tasks.named<Jar>("jar") {
  val otelJava = project.the<OtelJavaExtension>()
  bundle {
    // javax.annotation.* is always an optional import; modules can add more (typically
    // corresponding to compileOnly dependencies). The trailing "*" imports everything else.
    val optionalPackages = mutableListOf("javax.annotation")
    optionalPackages.addAll(otelJava.osgiOptionalPackages.get())
    // Order matters - bnd uses the first matching clause per package: explicit per-package clauses
    // (version ranges / resolution:=optional) first, then the wildcard optional packages, then the
    // "*" catch-all.
    val importClauses =
      otelJava.osgiImportPackages.get() +
        optionalPackages.map { "$it.*;resolution:=optional" } +
        "*"
    val importPackages = importClauses.joinToString(",")

    bnd(
      mapOf(
        "-exportcontents" to "io.opentelemetry.*",
        "Import-Package" to importPackages,
        // Auto-generate Provide/Require-Capability headers from META-INF/services entries
        // (including AutoService-generated providers such as the resources module's
        // ResourceProvider) so a ServiceLoader mediator (e.g. SPI Fly) can bridge them.
        "-metainf-services" to "auto",
        // reproducible builds (https://github.com/bndtools/bnd/issues/3521)
        "-noextraheaders" to "true",
        "-snapshot" to "SNAPSHOT",
      ),
    )
  }
}
