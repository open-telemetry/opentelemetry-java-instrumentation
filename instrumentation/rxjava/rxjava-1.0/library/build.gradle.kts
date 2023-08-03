plugins {
  id("otel.library-instrumentation")
}

// module name
val moduleName: String by extra("io.opentelemetry.instrumentation.rxjava.v1_0")

dependencies {
  library("io.reactivex:rxjava:1.0.7")
}
