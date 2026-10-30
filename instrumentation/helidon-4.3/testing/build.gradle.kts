plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
  if (otelProps.testLatestDeps) {
    javaToolchainVersion.set(JavaVersion.VERSION_27)
  }
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")
  implementation("io.helidon.webserver:helidon-webserver:4.3.0")
}
