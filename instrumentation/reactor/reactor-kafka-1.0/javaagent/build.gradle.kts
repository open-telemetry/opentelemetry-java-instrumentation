plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.projectreactor.kafka")
    module.set("reactor-kafka")
    // TODO: add support for 1.3
    versions.set("[1.0.0,1.3.0)")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  bootstrap(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  library("io.projectreactor.kafka:reactor-kafka:1.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testImplementation("org.testcontainers:kafka")

  testLibrary("io.projectreactor:reactor-test:3.1.0.RELEASE")

  latestDepTestLibrary("io.projectreactor:reactor-core:3.4.+")
  // TODO: add support for 1.3
  latestDepTestLibrary("io.projectreactor.kafka:reactor-kafka:1.2.+")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites)
  }
}
