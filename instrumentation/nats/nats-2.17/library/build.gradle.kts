plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.nats:jnats:2.21.5")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}
