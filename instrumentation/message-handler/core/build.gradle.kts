plugins {
  id("otel.library-instrumentation")
}

dependencies {
  api(project(":instrumentation:aws-lambda:aws-lambda-core-1.0:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk")
}
