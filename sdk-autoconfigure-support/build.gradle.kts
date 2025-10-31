plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-incubator")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-incubator")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
  testImplementation(project(":testing-common"))
}
