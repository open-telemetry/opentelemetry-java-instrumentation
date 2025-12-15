plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  implementation("io.opentelemetry:opentelemetry-api-incubator")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
}
