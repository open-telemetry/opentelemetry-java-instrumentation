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

  testImplementation(project(":instrumentation:rocketmq:rocketmq-client:rocketmq-client-5.0:testing"))
}

tasks {
  val testReceiveSpanDisabled by registering(Test::class) {
    filter {
      includeTestsMatching("RocketMqClientTest.testSendAndConsumeMessageWithReceiveSpanSuppressed")
    }
    include("**/RocketMqClientTest.*")
  }

  test {
    filter {
      includeTestsMatching("RocketMqClientTest.testSendAndConsumeMessage")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpanDisabled)
  }
}
