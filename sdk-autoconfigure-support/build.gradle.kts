plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
}
