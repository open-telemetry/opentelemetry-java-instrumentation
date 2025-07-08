plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
  testImplementation(project(":testing-common"))
}
