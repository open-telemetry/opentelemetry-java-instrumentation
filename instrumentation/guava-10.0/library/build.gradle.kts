plugins {
  id("otel.library-instrumentation")
}

// Override the enforced Guava BOM only when testing the supported baseline.
if (!otelProps.testLatestDeps) {
  configurations
    .matching { it.name == "compileClasspath" || it.name == "testCompileClasspath" || it.name == "testRuntimeClasspath" }
    .configureEach {
      resolutionStrategy.force("com.google.guava:guava:10.0")
    }
}

dependencies {
  library("com.google.guava:guava:10.0")
  implementation(project(":instrumentation-annotations-support"))
}
