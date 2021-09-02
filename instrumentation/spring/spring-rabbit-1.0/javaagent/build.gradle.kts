plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.amqp")
    module.set("spring-rabbit")
    versions.set("(,)")
    // Problematic release depending on snapshots
    skip("1.6.4.RELEASE", "2.1.1.RELEASE")
  }
}

dependencies {
  library("org.springframework.amqp:spring-rabbit:1.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:rabbitmq-2.7:javaagent"))

  // 2.1.7 adds the @RabbitListener annotation, we need that for tests
  testLibrary("org.springframework.amqp:spring-rabbit:2.1.7.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-test:1.5.22.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter:1.5.22.RELEASE")
}

tasks {
  named<Test>("test") {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
