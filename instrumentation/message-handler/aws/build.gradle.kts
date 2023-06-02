plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-sdk")

  implementation(project(":instrumentation:message-handler:core"))

  library("software.amazon.awssdk:sqs:2.2.0")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
}
