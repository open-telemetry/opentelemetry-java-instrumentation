plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk")

  implementation(project(":instrumentation:message-handler:core"))

  library("com.amazonaws:aws-lambda-java-core:1.0.0")
  library("com.amazonaws:aws-lambda-java-events:2.2.1")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
}
