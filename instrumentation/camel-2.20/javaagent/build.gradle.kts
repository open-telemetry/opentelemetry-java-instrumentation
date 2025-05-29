plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.camel")
    module.set("camel-core")
    versions.set("[2.19,3)")
    assertInverse.set(true)
  }
}

val camelversion = "2.20.1" // first version that the tests pass on

description = "camel-2-20"

dependencies {
  library("org.apache.camel:camel-core:$camelversion")
  implementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")

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

  testImplementation("org.apache.camel:camel-spring-boot-starter:$camelversion")
  testImplementation("org.apache.camel:camel-jetty-starter:$camelversion")
  testImplementation("org.apache.camel:camel-http-starter:$camelversion")
  testImplementation("org.apache.camel:camel-jaxb-starter:$camelversion")
  testImplementation("org.apache.camel:camel-undertow:$camelversion")
  testImplementation("org.apache.camel:camel-aws:$camelversion")
  testImplementation("org.apache.camel:camel-cassandraql:$camelversion")

  testImplementation("org.springframework.boot:spring-boot-starter-test:1.5.17.RELEASE")
  testImplementation("org.springframework.boot:spring-boot-starter:1.5.17.RELEASE")

  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.elasticmq:elasticmq-rest-sqs_2.13")

  testImplementation("org.testcontainers:cassandra")
  testImplementation("org.testcontainers:testcontainers")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("com.datastax.oss:java-driver-core:4.16.0") {
    exclude(group = "io.dropwizard.metrics", module = "metrics-core")
  }

  latestDepTestLibrary("org.apache.camel:camel-core:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-spring-boot-starter:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-jetty-starter:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-http-starter:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-jaxb-starter:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-undertow:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-aws:2.+") // documented limitation
  latestDepTestLibrary("org.apache.camel:camel-cassandraql:2.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.camel.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.aws-sdk.experimental-span-attributes=true")

    // TODO: fix camel instrumentation so that it uses semantic attributes extractors
    jvmArgs("-Dotel.instrumentation.experimental.span-suppression-strategy=span-kind")

    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
