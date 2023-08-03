plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.guava.v10_0")

dependencies {
  library("com.google.guava:guava:10.0")
  implementation(project(":instrumentation-annotations-support"))
}
