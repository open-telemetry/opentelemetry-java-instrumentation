plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

dependencies {
  implementation("io.helidon.webserver:helidon-webserver:4.3.0")
  testImplementation(project(":instrumentation:helidon:helidon-4.3.0:testing"))
}
