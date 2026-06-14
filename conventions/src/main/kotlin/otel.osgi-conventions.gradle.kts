import io.opentelemetry.instrumentation.gradle.OtelJavaExtension

// Generates OSGi bundle metadata in the jar manifest so published library artifacts can be consumed
// directly in OSGi runtimes. Apply only to modules published in the io.opentelemetry.instrumentation
// group (library instrumentations, SDK extensions, and the API/annotation modules) - never to
// shadowed javaagent artifacts.

plugins {
  `java-library`
  id("biz.aQute.bnd.builder")
}

// Configured via tasks.named (not afterEvaluate): the action runs when the jar task is realized,
// after each module's build script has set the otelJava properties below.
tasks.named<Jar>("jar") {
  val otelJava = project.the<OtelJavaExtension>()
  if (otelJava.osgiEnabled.get()) {
    bundle {
      // javax.annotation.* is always an optional import; modules can add more (typically
      // corresponding to compileOnly dependencies). The trailing "*" imports everything else.
      val optionalPackages = mutableListOf("javax.annotation")
      optionalPackages.addAll(otelJava.osgiOptionalPackages.get())
      val importPackages =
        optionalPackages.joinToString(",") { "$it.*;resolution:=optional" } + ",*"

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
}
