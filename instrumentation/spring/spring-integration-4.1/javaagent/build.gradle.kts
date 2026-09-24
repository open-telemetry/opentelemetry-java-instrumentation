plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.integration")
    module.set("spring-integration-core")
    versions.set("[4.1.0.RELEASE,)")
    assertInverse.set(true)
    excludeInstrumentationName("spring-integration-amqp-4.1")
  }
  pass {
    group.set("org.springframework.integration")
    module.set("spring-integration-amqp")
    versions.set("[4.1.0.RELEASE,)")
    assertInverse.set(true)
    excludeInstrumentationName("spring-integration-core-4.1")
  }
}

dependencies {
  implementation(project(":instrumentation:spring:spring-integration-4.1:library"))

  library("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")
  compileOnly("org.springframework.integration:spring-integration-amqp:4.1.0.RELEASE")

  testInstrumentation(project(":instrumentation:rabbitmq-2.7:javaagent"))
  testInstrumentation(project(":instrumentation:spring:spring-rabbit-1.0:javaagent"))

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

tasks {
  val testAmqpHandoffWithRabbitInstrumentation =
    register<Test>("testAmqpHandoffWithRabbitInstrumentation") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("MessageProducerSupportInstrumentationTest")
      }
      include("**/MessageProducerSupportInstrumentationTest.*")
      jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
      jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
      systemProperty("springIntegrationRabbitHandoffTest", "true")
      systemProperty("metadataConfig", "otel.instrumentation.spring-rabbit.enabled=true")
    }

  val testAmqpHandoffWithRabbitInstrumentationMessagingPreview =
    register<Test>("testAmqpHandoffWithRabbitInstrumentationMessagingPreview") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("MessageProducerSupportInstrumentationTest")
      }
      include("**/MessageProducerSupportInstrumentationTest.*")
      jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
      jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("springIntegrationRabbitHandoffTest", "true")
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.spring-rabbit.enabled=true,otel.semconv-stability.preview=messaging",
      )
    }

  val testAmqpHandoffWithRabbitInstrumentationBothSemconv =
    register<Test>("testAmqpHandoffWithRabbitInstrumentationBothSemconv") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("MessageProducerSupportInstrumentationTest")
      }
      include("**/MessageProducerSupportInstrumentationTest.*")
      jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
      jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
      jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
      systemProperty("springIntegrationRabbitHandoffTest", "true")
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.spring-rabbit.enabled=true,otel.semconv-stability.preview=messaging/dup",
      )
    }

  val testWithRabbitInstrumentation = register<Test>("testWithRabbitInstrumentation") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SpringIntegrationAndRabbitTest")
    }
    include("**/SpringIntegrationAndRabbitTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=true")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.spring-rabbit.enabled=true")
  }

  val testWithRabbitInstrumentationMessagingPreview =
    register<Test>("testWithRabbitInstrumentationMessagingPreview") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      filter {
        includeTestsMatching("SpringIntegrationAndRabbitTest")
      }
      include("**/SpringIntegrationAndRabbitTest.*")
      jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=true")
      jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
      jvmArgs("-Dotel.semconv-stability.preview=messaging")
      systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
    }

  val testWithProducerInstrumentation = register<Test>("testWithProducerInstrumentation") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("SpringCloudStreamProducerTest")
    }
    include("**/SpringCloudStreamProducerTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-integration.producer.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.spring-integration.producer.enabled=true")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      excludeTestsMatching("SpringCloudStreamRabbitTest")
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-integration.producer.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      excludeTestsMatching("SpringCloudStreamRabbitTest")
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-integration.producer.enabled=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  test {
    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      excludeTestsMatching("SpringCloudStreamProducerTest")
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
  }

  check {
    dependsOn(
      testAmqpHandoffWithRabbitInstrumentation,
      testAmqpHandoffWithRabbitInstrumentationMessagingPreview,
      testAmqpHandoffWithRabbitInstrumentationBothSemconv,
      testWithRabbitInstrumentation,
      testWithRabbitInstrumentationMessagingPreview,
      testWithProducerInstrumentation,
      testMessagingPreview,
      testBothSemconv,
    )
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
