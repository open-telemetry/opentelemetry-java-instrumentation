plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework")
    module.set("spring-jms")
    versions.set("[6.0.0,)")
    extraDependency("jakarta.jms:jakarta.jms-api:3.0.0")
    excludeInstrumentationName("jms")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
  implementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))
  implementation(project(":instrumentation:jms:jms-3.0:javaagent"))

  library("org.springframework:spring-jms:6.0.0")
  compileOnly("org.springframework:spring-context:6.0.0")
  compileOnly("jakarta.jms:jakarta.jms-api:3.0.0")

  testInstrumentation(project(":instrumentation:jms:jms-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-jms:spring-jms-2.0:javaagent"))

  testImplementation("org.apache.activemq:artemis-jakarta-client:2.27.1")

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter:3.0.0")
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":javaagent-bootstrap"))
        implementation(project(":instrumentation:jms:jms-common-1.1:bootstrap"))
        implementation(project(":instrumentation:jms:jms-3.0:javaagent"))
        implementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))
        implementation(project(":javaagent-extension-api"))
        implementation("jakarta.jms:jakarta.jms-api:3.0.0")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SpringListenerSuppressReceiveSpansTest")
    }
    include("**/SpringListenerSuppressReceiveSpansTest.*")
  }

  val testJmsDisabled = register<Test>("testJmsDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    filter {
      includeTestsMatching("*.testSpringJmsListenerWithJmsDisabled")
    }
    jvmArgs("-Dotel.instrumentation.jms.enabled=false")
    // receive telemetry is enabled here because the jms instrumentation that would create the
    // receive operation is disabled, so the process operation owns the messaging telemetry
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")

    systemProperty("testJmsDisabled", "true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.jms.enabled=false," +
        "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  test {
    filter {
      excludeTestsMatching("SpringListenerSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      testReceiveSpansDisabled,
      testJmsDisabled,
    )
  }
}
