plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  // Only declarative-config users need the incubator model classes at runtime.
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  implementation("io.opentelemetry:opentelemetry-api-incubator")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
}
