plugins {
  id("otel.library-instrumentation")
}

otelJava {
  // OpenTelemetryDispatcherFactory needs package-private access to io.nats.client.impl, which
  // doesn't work in OSGi where each bundle has its own class loader
  osgiEnabled.set(false)
}

dependencies {
  library("io.nats:jnats:2.17.2")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:nats:nats-2.17:testing"))
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
