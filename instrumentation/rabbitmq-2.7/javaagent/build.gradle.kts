plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.rabbitmq")
    module.set("amqp-client")
    versions.set("[2.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.rabbitmq:amqp-client:2.7.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE") {
    exclude("com.rabbitmq", "amqp-client")
  }

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  testLibrary("io.projectreactor.rabbitmq:reactor-rabbitmq:1.0.0.RELEASE")
  // since reactor-rabbitmq:1.5.4 there is only a runtime dependency to reactor-core but spock
  // needs it at compile time
  testCompileOnly("io.projectreactor:reactor-core:3.4.12")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.rabbitmq.experimental-span-attributes=true")
  usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
}
