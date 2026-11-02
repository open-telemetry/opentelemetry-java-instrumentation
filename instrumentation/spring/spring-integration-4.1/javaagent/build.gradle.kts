import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.springframework.integration")
    module.set("spring-integration-core")
    versions.set("[4.1.0.RELEASE,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationAmqpInstrumentationModule")
  }
  pass {
    group.set("org.springframework.integration")
    module.set("spring-integration-amqp")
    versions.set("[4.1.0.RELEASE,)")
    assertInverse.set(true)
    excludeInstrumentationModule("io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1.SpringIntegrationInstrumentationModule")
  }
}

dependencies {
  implementation(project(":instrumentation:spring:spring-integration-4.1:library"))

  library("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")
  compileOnly("org.springframework.integration:spring-integration-amqp:4.1.0.RELEASE")

  testInstrumentation(project(":instrumentation:rabbitmq-2.7:javaagent"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-rabbit-1.1:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-integration-4.1:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:1.5.22.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter:1.5.22.RELEASE")
  testLibrary("org.springframework.cloud:spring-cloud-stream:2.2.1.RELEASE")
  testLibrary("org.springframework.cloud:spring-cloud-stream-binder-rabbit:2.2.1.RELEASE")

  testImplementation("javax.servlet:javax.servlet-api:3.1.0")

  latestDepTestLibrary("org.springframework.integration:spring-integration-core:5.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter-test:2.+") // documented limitation
  latestDepTestLibrary("org.springframework.boot:spring-boot-starter:2.+") // documented limitation
  latestDepTestLibrary("org.springframework.cloud:spring-cloud-stream:3.+") // documented limitation
  latestDepTestLibrary("org.springframework.cloud:spring-cloud-stream-binder-rabbit:3.+") // documented limitation
}

testing.suites.executions("test") {
  register("AmqpHandoffWithRabbitInstrumentation") {
    filter {
      includeTestsMatching("MessageProducerSupportInstrumentationTest")
    }
    include("**/MessageProducerSupportInstrumentationTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    systemProperty("springIntegrationRabbitHandoffTest", "true")
  }

  register("WithRabbitInstrumentation") {
    filter {
      includeTestsMatching("SpringIntegrationAndRabbitTest")
    }
    include("**/SpringIntegrationAndRabbitTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=true")
    telemetryConfig("otel.instrumentation.spring-rabbit.enabled", "true")
  }

  register("WithProducerInstrumentation") {
    filter {
      includeTestsMatching("SpringCloudStreamProducerTest")
    }
    include("**/SpringCloudStreamProducerTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    telemetryConfig("otel.instrumentation.spring-integration.producer.enabled", "true")
  }

  register("ProducerEnabled") {
    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      excludeTestsMatching("SpringCloudStreamRabbitTest")
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    telemetryConfig("otel.instrumentation.spring-integration.producer.enabled", "true")
  }

  configureDefault {
    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      excludeTestsMatching("SpringCloudStreamProducerTest")
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
  }
}

testing {
  suites {
    register<JvmTestSuite>("version41Test") {
      dependencies {
        implementation(project(":instrumentation:spring:spring-integration-4.1:testing"))
        implementation("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")
        implementation("org.springframework.integration:spring-integration-amqp:4.1.0.RELEASE")
      }
      targets.all {
        testTask.configure {
          // Exercise channel propagation independently of executor instrumentation.
          jvmArgs("-Dotel.instrumentation.executors.enabled=false")
          jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
          jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
