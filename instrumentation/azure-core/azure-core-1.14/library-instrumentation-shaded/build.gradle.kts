plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

val adapterVersion = "1.0.0-beta.12"
val adapterSources = configurations.create("adapterSources") {
  isCanBeConsumed = false
  isTransitive = false
}

dependencies {
  // this is the latest version that works with azure-core 1.14
  implementation("com.azure:azure-core-tracing-opentelemetry:$adapterVersion")
  add(adapterSources.name, "com.azure:azure-core-tracing-opentelemetry:$adapterVersion:sources")
}

// beta.12 injects the ambient parent instead of its HTTP span. Recompile just that policy,
// retaining the compatible adapter and its service providers rather than upgrading to beta.23.
val patchHttpPolicy = tasks.register<Sync>("patchHttpPolicy") {
  from({ zipTree(adapterSources.singleFile) }) {
    include("com/azure/core/tracing/opentelemetry/OpenTelemetryHttpPolicy.java")
    filter { line: String ->
      val original = "traceContextFormat.inject(io.opentelemetry.context.Context.current(), request, contextSetter);"
      if (line.contains("traceContextFormat.inject(")) {
        check(line.trim() == original) { "Unexpected beta.12 HTTP propagation source" }
        line.replace("Context.current()", "Context.current().with(span)")
      } else {
        line
      }
    }
  }
  into(layout.buildDirectory.dir("generated/sources/patchedAdapter"))
  doLast {
    val source = destinationDir.resolve("com/azure/core/tracing/opentelemetry/OpenTelemetryHttpPolicy.java")
    val injection = "traceContextFormat.inject(io.opentelemetry.context.Context.current().with(span), request, contextSetter);"
    check(source.readLines().count { it.trim() == injection } == 1) {
      "Expected exactly one patched beta.12 HTTP propagation call"
    }
  }
}

sourceSets.main {
  java.srcDir(patchHttpPolicy)
}

tasks {
  named<Checkstyle>("checkstyleMain") {
    // Preserve the upstream policy's formatting in the generated source.
    exclude("com/azure/core/tracing/opentelemetry/OpenTelemetryHttpPolicy.java")
  }

  shadowJar {
    // Project classes precede dependencies, so use the recompiled policy instead of beta.12's class.
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/services/*")

    dependencies {
      // including only azure-core-tracing-opentelemetry excludes its transitive dependencies
      include(dependency("com.azure:azure-core-tracing-opentelemetry"))
    }
    relocate("com.azure.core.tracing.opentelemetry", "io.opentelemetry.javaagent.instrumentation.azurecore.v1_14.shaded.com.azure.core.tracing.opentelemetry")
  }

  register<Copy>("extractShadowJar") {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
