plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.instrumentation:opentelemetry-instrumentation-testing")

  compileOnly("com.datastax.oss:java-driver-core:4.4.0")
  implementation("io.projectreactor:reactor-core:3.5.3")

  api(project(":instrumentation:cassandra:cassandra-common-4.0:testing"))
}
