plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

dependencies {
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
  api("io.opentelemetry:opentelemetry-sdk-extension-incubator")

  compileOnly("com.google.code.findbugs:annotations")
  testCompileOnly("com.google.code.findbugs:annotations")
  testImplementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
}
