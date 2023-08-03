plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.netty.common")

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
