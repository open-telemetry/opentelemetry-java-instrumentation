plugins {
  id("otel.javaagent-testing")
}

dependencies {
  library("org.apache.kafka:kafka-clients:2.4.0")

  testInstrumentation(project(":instrumentation:kafka-clients:kafka-clients-0.11:javaagent"))

  testLibrary("org.springframework.kafka:spring-kafka:2.4.0.RELEASE")
  testLibrary("org.springframework.kafka:spring-kafka-test:2.4.0.RELEASE")
  testLibrary("org.springframework:spring-core:5.2.9.RELEASE")
  testImplementation("javax.xml.bind:jaxb-api:2.2.3")

  latestDepTestLibrary("org.apache.kafka:kafka-clients:2.+")
  latestDepTestLibrary("org.apache.kafka:kafka_2.13:2.+")
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
