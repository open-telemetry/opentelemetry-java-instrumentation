plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly(project(":instrumentation:logback:logback-mdc-1.0:library"))

  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  api("ch.qos.logback:logback-classic:1.0.0")

  implementation("io.opentelemetry:opentelemetry-api")
}
