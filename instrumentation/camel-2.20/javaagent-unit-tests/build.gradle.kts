plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:camel-2.20:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))

  testImplementation("org.apache.camel:camel-core:2.20.1")
  testImplementation("org.apache.camel:camel-aws:2.20.1")
  testImplementation("org.apache.camel:camel-http:2.20.1")

  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
}
