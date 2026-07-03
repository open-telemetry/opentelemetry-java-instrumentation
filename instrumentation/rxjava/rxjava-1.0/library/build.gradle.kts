plugins {
  id("otel.library-instrumentation")
}

otelJava {
  // OpenTelemetryTracingUtil needs package-private access to the rx package, which doesn't work
  // in OSGi where each bundle has its own class loader
  osgiEnabled.set(false)
}

dependencies {
  library("io.reactivex:rxjava:1.0.7")
}
