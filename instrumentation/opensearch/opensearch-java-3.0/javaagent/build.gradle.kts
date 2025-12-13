plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.opensearch.client")
    module.set("opensearch-java")
    versions.set("[3.0,)")
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("org.opensearch.client:opensearch-java:3.0.0")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
  implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

  testImplementation("org.opensearch.client:opensearch-rest-client:3.0.0")
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common:testing"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))

  // AwsSdk2Transport supports awssdk version 2.26.0
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testImplementation("software.amazon.awssdk:auth:2.26.0")
  testImplementation("software.amazon.awssdk:identity-spi:2.26.0")
  testImplementation("software.amazon.awssdk:apache-client:2.26.0")
  testImplementation("software.amazon.awssdk:netty-nio-client:2.26.0")
  testImplementation("software.amazon.awssdk:netty-nio-client:2.26.0")
  testImplementation("software.amazon.awssdk:regions:2.26.0")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  test {
    filter {
      excludeTestsMatching("OpenSearchCaptureSearchQueryTest")
    }
  }

  val testCaptureSearchQuery by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("OpenSearchCaptureSearchQueryTest")
    }
    jvmArgs("-Dotel.instrumentation.opensearch.capture-search-query=true")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("OpenSearchCaptureSearchQueryTest")
    }
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testCaptureSearchQueryStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("OpenSearchCaptureSearchQueryTest")
    }
    jvmArgs("-Dotel.instrumentation.opensearch.capture-search-query=true")
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testCaptureSearchQuery)
    dependsOn(testStableSemconv)
    dependsOn(testCaptureSearchQueryStableSemconv)
  }
}
