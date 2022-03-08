plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.kafka")
    module.set("spring-kafka")
    versions.set("[2.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  library("org.springframework.kafka:spring-kafka:2.7.0")

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.testcontainers:kafka")

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
  testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }
}
