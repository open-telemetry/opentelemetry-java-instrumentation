plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  library("org.apache.kafka:kafka-clients:2.6.0")

  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.10.2")

  testImplementation("org.testcontainers:kafka")
  testImplementation("org.testcontainers:junit-jupiter")

  testCompileOnly("com.google.auto.value:auto-value-annotations")
  testAnnotationProcessor("com.google.auto.value:auto-value")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("InterceptorsSuppressReceiveSpansTest")
      includeTestsMatching("WrapperSuppressReceiveSpansTest")
    }
    include("**/InterceptorsSuppressReceiveSpansTest.*", "**/WrapperSuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("InterceptorsSuppressReceiveSpansTest")
      excludeTestsMatching("WrapperSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty("otel.instrumentation.messaging.experimental.capture-headers", "Test-Message-Header")
  }

  check {
    dependsOn(testReceiveSpansDisabled)
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

// kafka 4.1 requires java 11
if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
