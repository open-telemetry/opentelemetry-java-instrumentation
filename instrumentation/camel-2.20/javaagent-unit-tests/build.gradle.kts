plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:camel-2.20:javaagent"))
  testImplementation(
    project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"),
  )
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testImplementation(
    project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"),
  )
  testImplementation(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))

  testImplementation("org.apache.camel:camel-core:2.20.1")
  testImplementation("org.apache.camel:camel-aws:2.20.1")
  testImplementation("org.apache.camel:camel-http:2.20.1")
  testImplementation("org.apache.camel:camel-rabbitmq:2.25.1") {
    exclude(group = "org.apache.camel", module = "camel-core")
  }
  testImplementation("org.apache.kafka:kafka-clients:0.11.0.0")
  testImplementation("javax.jms:jms-api:1.1-rev-1")

  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
}
