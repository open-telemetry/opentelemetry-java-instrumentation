plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.rocketmq")
    module.set("rocketmq-client-java")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.rocketmq:rocketmq-client-java:5.0.0")

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-5.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testReceiveSpanDisabled by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    include("**/RocketMqClientSuppressReceiveSpanTest.*")
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")

    jvmArgs("-Dotel.instrumentation.rocketmq-client.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.rocketmq-client.experimental-span-attributes=true")
  }

  val testExceptionSignalLogs by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.semconv.exception.signal.opt-in=logs")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.semconv.exception.signal.opt-in=logs")
  }

  test {
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpanDisabled, testExperimental, testExceptionSignalLogs)
  }

  if (findProperty("denyUnsafe") as Boolean) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
