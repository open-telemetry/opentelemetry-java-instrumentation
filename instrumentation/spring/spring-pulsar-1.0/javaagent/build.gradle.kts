plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.pulsar")
    module.set("spring-pulsar")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("pulsar-2.8")
  }
}

dependencies {
  library("org.springframework.pulsar:spring-pulsar:1.0.0")
  implementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))

  testInstrumentation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-pulsar-1.0:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.2.4")
  testLibrary("org.springframework.boot:spring-boot-starter:3.2.4")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

testing {
  suites {
    val testReceiveSpansDisabled by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:spring:spring-pulsar-1.0:testing"))

        if (latestDepTest) {
          implementation("org.springframework.pulsar:spring-pulsar:latest.release")
          implementation("org.springframework.boot:spring-boot-starter-test:latest.release")
          implementation("org.springframework.boot:spring-boot-starter:latest.release")
        } else {
          implementation("org.springframework.pulsar:spring-pulsar:1.0.0")
          implementation("org.springframework.boot:spring-boot-starter-test:3.2.4")
          implementation("org.springframework.boot:spring-boot-starter:3.2.4")
        }
      }

      targets {
        all {
          testTask.configure {
            usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

            jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
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

    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=false")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")

    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testing.suites)
  }
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
