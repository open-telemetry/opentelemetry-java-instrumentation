plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-streams")
    versions.set("[0.11.0.0,3)")
  }
}

testSets {
  create("latestDepTest")
}

dependencies {
  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:javaagent"))

  compileOnly("org.apache.kafka:kafka-streams:0.11.0.0")

  // Include kafka-clients instrumentation for tests.
  testInstrumentation(project(":instrumentation:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.apache.kafka:kafka-streams:0.11.0.0")
  testImplementation("org.apache.kafka:kafka-clients:0.11.0.0")
  testImplementation("org.springframework.kafka:spring-kafka:1.3.3.RELEASE")
  testImplementation("org.springframework.kafka:spring-kafka-test:1.3.3.RELEASE")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testImplementation("org.assertj:assertj-core")

  add("latestDepTestImplementation", "org.apache.kafka:kafka_2.13:2.+")
  add("latestDepTestImplementation", "org.apache.kafka:kafka-clients:2.+")
  add("latestDepTestImplementation", "org.apache.kafka:kafka-streams:2.+")
  add("latestDepTestImplementation", "org.springframework.kafka:spring-kafka:+")
  add("latestDepTestImplementation", "org.springframework.kafka:spring-kafka-test:+")
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  if (findProperty("testLatestDeps") as Boolean) {
    // latestDepTest is still run
    named("test") {
      enabled = false
    }
  }
}

// Requires old version of AssertJ for baseline
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.testRuntimeClasspath.resolutionStrategy.force("org.assertj:assertj-core:2.9.1")
}
