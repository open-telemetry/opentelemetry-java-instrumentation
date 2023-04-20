plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk")

  library("com.amazonaws:aws-lambda-java-core:1.0.0")
  library("com.amazonaws:aws-lambda-java-events:2.2.1")

  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
}
