plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  library("org.springframework.integration:spring-integration-core:4.1.0.RELEASE")

  testImplementation(project(":instrumentation:spring:spring-integration-4.1:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:1.5.22.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter:1.5.22.RELEASE")
  testLibrary("org.springframework.cloud:spring-cloud-stream:2.2.1.RELEASE")
  testLibrary("org.springframework.cloud:spring-cloud-stream-binder-rabbit:2.2.1.RELEASE")
}

tasks {
  val testWithProducerInstrumentation by registering(Test::class) {
    filter {
      includeTestsMatching("SpringCloudStreamProducerTest")
    }
    include("**/SpringCloudStreamProducerTest.*")
    jvmArgs("-Dotel.instrumentation.spring-integration.producer.enabled=true")
  }

  test {
    filter {
      excludeTestsMatching("SpringCloudStreamProducerTest")
    }
  }

  check {
    dependsOn(testWithProducerInstrumentation)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
