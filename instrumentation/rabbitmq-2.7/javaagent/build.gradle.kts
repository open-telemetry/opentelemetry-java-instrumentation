import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.testcontainers-testing")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.rabbitmq")
    module.set("amqp-client")
    versions.set("[2.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.rabbitmq:amqp-client:2.7.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE") {
    exclude("com.rabbitmq", "amqp-client")
  }

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

testing.suites {
  register<JvmTestSuite>("reactorTest") {
    dependencies {
      implementation("io.projectreactor.rabbitmq:reactor-rabbitmq:${baseVersion("1.0.0.RELEASE").orLatest()}")
      implementation("com.rabbitmq:amqp-client:${baseVersion("5.5.1").orLatest()}")
    }
  }
  register<JvmTestSuite>("recoveryTest") {
    dependencies {
      implementation("com.rabbitmq:amqp-client:${baseVersion("4.0.0").orLatest()}")
    }
  }
  withType<JvmTestSuite>().configureEach {
    sources.java.srcDir("src/testCommon/java")
    dependencies {
      implementation("org.testcontainers:testcontainers")
    }
    val baseline = name == "test"
    executions {
      configureEach {
        telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
      }
      register("Experimental") {
        telemetryConfig("otel.instrumentation.rabbitmq.experimental-span-attributes", "true")
      }
      if (baseline) {
        register("ReceiveSpansDisabled") {
          telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "false")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)

    // add byte buddy agent for mockito
    configurations.testRuntimeClasspath.get().find { it.name.contains("byte-buddy-agent") }?.apply {
      jvmArgs("-javaagent:$absolutePath")
    }
  }
}
