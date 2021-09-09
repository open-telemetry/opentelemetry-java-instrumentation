plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.kafka")
    module.set("kafka-clients")
    versions.set("[0.11.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:javaagent"))

  library("org.apache.kafka:kafka-clients:0.11.0.0")

  testLibrary("org.springframework.kafka:spring-kafka:1.3.3.RELEASE")
  testLibrary("org.springframework.kafka:spring-kafka-test:1.3.3.RELEASE")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")
  testLibrary("org.assertj:assertj-core")

  // Include latest version of kafka itself along with latest version of client libs.
  // This seems to help with jar compatibility hell.
  latestDepTestLibrary("org.apache.kafka:kafka_2.11:2.3.+")
  // (Pinning to 2.3.x: 2.4.0 introduces an error when executing compileLatestDepTestGroovy)
  //  Caused by: java.lang.NoClassDefFoundError: org.I0Itec.zkclient.ZkClient
  latestDepTestLibrary("org.apache.kafka:kafka-clients:2.3.+")
  latestDepTestLibrary("org.springframework.kafka:spring-kafka:2.2.+")
  latestDepTestLibrary("org.springframework.kafka:spring-kafka-test:2.2.+")
  // assertj-core:3.20.0 is incompatible with spring-kafka-test:2.7.2
  latestDepTestLibrary("org.assertj:assertj-core:3.19.0")
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }

  val testPropagationDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("KafkaClientPropagationDisabledTest")
      isFailOnNoMatchingTests = false
    }
    include("**/KafkaClientPropagationDisabledTest.*")
    jvmArgs("-Dotel.instrumentation.kafka.client-propagation.enabled=false")
  }

  named<Test>("test") {
    dependsOn(testPropagationDisabled)
    filter {
      excludeTestsMatching("KafkaClientPropagationDisabledTest")
      isFailOnNoMatchingTests = false
    }
  }
}

// Requires old version of AssertJ for baseline
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.testRuntimeClasspath.resolutionStrategy.force("org.assertj:assertj-core:2.9.1")
}
