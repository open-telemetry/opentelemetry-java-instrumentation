plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.nullaway-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  // DefaultInstrumentationConfig and DefaultInstrumentationConfigApplier expose declarative config
  // model types in their public APIs.
  api("io.opentelemetry:opentelemetry-sdk-extension-declarative-config")
  implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  implementation("io.opentelemetry:opentelemetry-api-incubator")

  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-declarative-config")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
}
