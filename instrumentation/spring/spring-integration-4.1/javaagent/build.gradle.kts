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
}

tasks {
  val testWithRabbitInstrumentation by registering(Test::class) {
    filter {
      includeTestsMatching("SpringIntegrationAndRabbitTest")
      isFailOnNoMatchingTests = false
    }
    include("**/SpringIntegrationAndRabbitTest.*")
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=true")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=true")
  }

  named<Test>("test") {
    dependsOn(testWithRabbitInstrumentation)

    filter {
      excludeTestsMatching("SpringIntegrationAndRabbitTest")
      isFailOnNoMatchingTests = false
    }
    jvmArgs("-Dotel.instrumentation.rabbitmq.enabled=false")
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
