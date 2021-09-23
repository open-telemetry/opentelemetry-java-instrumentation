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

  implementation(project(":instrumentation:kafka-clients:kafka-clients-common:javaagent"))

  library("org.springframework.kafka:spring-kafka:2.7.0")

  testInstrumentation(project(":instrumentation:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.testcontainers:kafka")

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
  testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
  }
}
