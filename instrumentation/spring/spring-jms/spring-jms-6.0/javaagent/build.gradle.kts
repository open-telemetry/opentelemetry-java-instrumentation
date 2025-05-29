plugins {
  id("otel.javaagent-instrumentation")
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
  bootstrap(project(":instrumentation:jms:jms-common:bootstrap"))
  implementation(project(":instrumentation:jms:jms-common:javaagent"))
  implementation(project(":instrumentation:jms:jms-3.0:javaagent"))

  library("org.springframework:spring-jms:6.0.0")
  compileOnly("jakarta.jms:jakarta.jms-api:3.0.0")

  testInstrumentation(project(":instrumentation:jms:jms-3.0:javaagent"))

  testImplementation("org.apache.activemq:artemis-jakarta-client:2.27.1")

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.0.0")
  testLibrary("org.springframework.boot:spring-boot-starter:3.0.0")
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("SpringListenerSuppressReceiveSpansTest")
    }
    include("**/SpringListenerSuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("SpringListenerSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpansDisabled)
  }
}
