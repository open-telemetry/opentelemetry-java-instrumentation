plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.camel")
    module.set("camel-core")
    versions.set("[2.20.1,3)")
  }
}

val camelversion = "2.20.1"
val versions: Map<String, String> by project

dependencies {
  library("org.apache.camel:camel-core:$camelversion")
  implementation("io.opentelemetry:opentelemetry-extension-aws")

  // without adding this dependency, javadoc fails:
  //   warning: unknown enum constant XmlAccessType.PROPERTY
  //   reason: class file for javax.xml.bind.annotation.XmlAccessType not found
  // due to usage of org.apache.camel.model.RouteDefinition in CamelTracingService
  // which has jaxb class-level annotations
  compileOnly("javax.xml.bind:jaxb-api:2.3.1")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:aws-sdk:aws-sdk-1.11:javaagent"))

  testLibrary("org.apache.camel:camel-spring-boot-starter:$camelversion")
  testLibrary("org.apache.camel:camel-jetty-starter:$camelversion")
  testLibrary("org.apache.camel:camel-http-starter:$camelversion")
  testLibrary("org.apache.camel:camel-jaxb-starter:$camelversion")
  testLibrary("org.apache.camel:camel-undertow:$camelversion")
  testLibrary("org.apache.camel:camel-aws:$camelversion")
  testLibrary("org.apache.camel:camel-cassandraql:$camelversion")

  testImplementation("org.springframework.boot:spring-boot-starter-test:1.5.17.RELEASE")
  testImplementation("org.springframework.boot:spring-boot-starter:1.5.17.RELEASE")

  testImplementation("org.spockframework:spock-spring:${versions["org.spockframework"]}")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.elasticmq:elasticmq-rest-sqs_2.12:1.0.0")

  testImplementation("org.testcontainers:localstack")
  testImplementation("org.testcontainers:cassandra")

  latestDepTestLibrary("org.apache.camel:camel-core:2.+")
  latestDepTestLibrary("org.apache.camel:camel-spring-boot-starter:2.+")
  latestDepTestLibrary("org.apache.camel:camel-jetty-starter:2.+")
  latestDepTestLibrary("org.apache.camel:camel-http-starter:2.+")
  latestDepTestLibrary("org.apache.camel:camel-jaxb-starter:2.+")
  latestDepTestLibrary("org.apache.camel:camel-undertow:2.+")
  latestDepTestLibrary("org.apache.camel:camel-aws:2.+")
  latestDepTestLibrary("org.apache.camel:camel-cassandraql:2.+")
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.apache-camel.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.aws-sdk.experimental-span-attributes=true")
  }
}
