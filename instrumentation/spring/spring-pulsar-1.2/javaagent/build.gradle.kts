plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.pulsar")
    module.set("spring-pulsar")
    versions.set("[1.2.0,]")
  }
}

dependencies {
  library("org.springframework.pulsar:spring-pulsar:1.2.0")

  testInstrumentation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))
  testImplementation(project(":instrumentation:spring:spring-pulsar-1.2:testing"))
  testLibrary("org.springframework.pulsar:spring-pulsar:1.2.0")

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.2.4")
  testLibrary("org.springframework.boot:spring-boot-starter:3.2.4")
}

testing {
  suites {
    val testReceiveSpansDisabled by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:spring:spring-pulsar-1.2:testing"))
        implementation("org.springframework.pulsar:spring-pulsar:1.2.0")
        implementation("org.springframework.boot:spring-boot-starter-test:3.2.4")
        implementation("org.springframework.boot:spring-boot-starter:3.2.4")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    dependsOn(testing.suites)
  }
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
