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

  testImplementation(project(":instrumentation:spring:spring-kafka-2.7:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
  testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
}

testing {
  suites {
    val testNoReceiveTelemetry by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.springframework.kafka:spring-kafka:2.7.0")
        implementation(project(":instrumentation:spring:spring-kafka-2.7:testing"))
        implementation("org.springframework.boot:spring-boot-starter-test:2.5.3")
        implementation("org.springframework.boot:spring-boot-starter:2.5.3")
      }

      targets {
        all {
          testTask.configure {
            usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

            jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=false")
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
          }
        }
      }
    }
  }
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
