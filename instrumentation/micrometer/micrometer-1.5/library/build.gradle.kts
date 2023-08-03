plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.micrometer.v1_5")

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-extension-incubator")

  library("io.micrometer:micrometer-core:1.5.0")

  testImplementation(project(":instrumentation:micrometer:micrometer-1.5:testing"))
}
