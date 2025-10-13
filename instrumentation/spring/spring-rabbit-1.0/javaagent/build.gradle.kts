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
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// spring 6 requires java 17
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

// spring 6 uses slf4j 2.0
if (!latestDepTest) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
