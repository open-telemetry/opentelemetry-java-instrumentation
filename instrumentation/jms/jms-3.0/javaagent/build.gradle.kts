plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.jms")
    module.set("jakarta.jms-api")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
  fail {
    group.set("javax.jms")
    module.set("jms-api")
    versions.set("(,)")
  }
  fail {
    group.set("javax.jms")
    module.set("javax.jms-api")
    versions.set("(,)")
  }
}

dependencies {
  implementation(project(":instrumentation:jms:jms-common:javaagent"))

  library("jakarta.jms:jakarta.jms-api:3.0.0")

  testImplementation("org.apache.activemq:artemis-jakarta-client:2.27.1")

  testInstrumentation(project(":instrumentation:jms:jms-1.1:javaagent"))
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testReceiveSpansDisabled by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    include("**/Jms3SuppressReceiveSpansTest.*")
  }

  test {
    filter {
      excludeTestsMatching("Jms3SuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testing.suites, testReceiveSpansDisabled)
  }
}
