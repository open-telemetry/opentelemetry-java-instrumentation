plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_21)
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("io.helidon.webserver:helidon-webserver:4.3.0")
}
