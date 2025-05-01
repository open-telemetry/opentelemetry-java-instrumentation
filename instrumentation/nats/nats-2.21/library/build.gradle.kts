plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("io.nats:jnats:2.21.0")

  testImplementation(project(":instrumentation:nats:nats-2.21:testing"))
}
