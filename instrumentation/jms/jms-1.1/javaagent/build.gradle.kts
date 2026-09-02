plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("javax.jms")
    module.set("jms-api")
    versions.set("(,)")
  }
  pass {
    group.set("javax.jms")
    module.set("javax.jms-api")
    versions.set("(,)")
  }
  pass {
    group.set("jakarta.jms")
    module.set("jakarta.jms-api")
    versions.set("(,3)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:jms:jms-common-1.1:javaagent"))

  bootstrap(project(":instrumentation:jms:jms-common-1.1:bootstrap"))

  compileOnly("javax.jms:jms-api:1.1-rev-1")

  testImplementation("org.apache.activemq:activemq-client:5.16.5")

  testInstrumentation(project(":instrumentation:jms:jms-3.0:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("jms2Test") {
      dependencies {
        implementation("org.hornetq:hornetq-jms-client:2.4.7.Final")
        implementation("org.hornetq:hornetq-jms-server:2.4.7.Final")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
            systemProperty(
              "metadataConfig",
              "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
            )
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testReceiveSpansDisabled = register<Test>("testReceiveSpansDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    filter {
      includeTestsMatching("Jms1SuppressReceiveSpansTest")
    }
    include("**/Jms1SuppressReceiveSpansTest.*")
  }

  val testMessagingPreviewReceiveSpansDisabled =
    register<Test>("testMessagingPreviewReceiveSpansDisabled") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

      filter {
        includeTestsMatching("Jms1SuppressReceiveSpansTest")
      }
      include("**/Jms1SuppressReceiveSpansTest.*")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    filter {
      excludeTestsMatching("Jms1SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testJms2MessagingPreview = register<Test>("testJms2MessagingPreview") {
    testClassesDirs = sourceSets["jms2Test"].output.classesDirs
    classpath = sourceSets["jms2Test"].runtimeClasspath
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testJms2BothSemconv = register<Test>("testJms2BothSemconv") {
    testClassesDirs = sourceSets["jms2Test"].output.classesDirs
    classpath = sourceSets["jms2Test"].runtimeClasspath
    isEnabled = project.tasks.named("jms2Test").get().enabled
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    filter {
      includeTestsMatching("Jms1InstrumentationTest")
    }
    include("**/Jms1InstrumentationTest.*")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    filter {
      excludeTestsMatching("Jms1SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      testReceiveSpansDisabled,
      testMessagingPreview,
      testMessagingPreviewReceiveSpansDisabled,
      testJms2MessagingPreview,
      testJms2BothSemconv,
      testBothSemconv,
    )
  }
}

configurations.configureEach {
  // this doesn't exist in maven central, and doesn't seem to be needed anyways
  // included from org.hornetq:hornetq-jms-server:2.4.7.Final
  exclude("org.jboss.naming", "jnpserver")
}
