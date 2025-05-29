plugins {
  id("otel.javaagent-instrumentation")
}

// context "leak" here is intentional: spring-integration instrumentation will always override
// "local" span context with one extracted from the incoming message when it decides to start a
// CONSUMER span
extra["failOnContextLeak"] = false

muzzle {
  pass {
    group.set("org.springframework.integration")
    module.set("spring-integration-core")
    versions.set("[4.1.0.RELEASE,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:spring:spring-integration-4.1:library"))

  library("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")

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
  val testWithRabbitInstrumentation by registering(Test::class) {
    filter {
      includeTestsMatching("SpringIntegrationAndRabbitTest")
    }
    include("**/SpringIntegrationAndRabbitTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=true")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
  }

  val testWithProducerInstrumentation by registering(Test::class) {
    filter {
      includeTestsMatching("SpringCloudStreamProducerTest")
    }
    include("**/SpringCloudStreamProducerTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-integration.producer.enabled=true")
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
    dependsOn(testWithRabbitInstrumentation)
    dependsOn(testWithProducerInstrumentation)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
