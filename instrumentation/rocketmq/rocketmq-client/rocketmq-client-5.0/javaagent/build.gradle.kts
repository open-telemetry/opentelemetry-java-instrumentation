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
      includeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    include("**/RocketMqClientSuppressReceiveSpanTest.*")
  }

  test {
    filter {
      excludeTestsMatching("RocketMqClientSuppressReceiveSpanTest")
    }
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }

  check {
    dependsOn(testReceiveSpanDisabled)
  }
}
