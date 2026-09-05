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
  compileOnly("com.fasterxml.jackson.core:jackson-core")

  testImplementation("org.opensearch.client:opensearch-rest-client:3.0.0")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation(project(":instrumentation:opensearch:opensearch-rest-common-1.0:testing"))
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-5.0:javaagent"))

  // AwsSdk2Transport supports awssdk version 2.26.0
  testInstrumentation(project(":instrumentation:apache-httpclient:apache-httpclient-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testImplementation("software.amazon.awssdk:auth:2.26.0")
  testImplementation("software.amazon.awssdk:identity-spi:2.26.0")
  testImplementation("software.amazon.awssdk:apache-client:2.26.0")
  testImplementation("software.amazon.awssdk:netty-nio-client:2.26.0")
  testImplementation("software.amazon.awssdk:regions:2.26.0")
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    filter {
      excludeTestsMatching("OpenSearchDisabledCaptureSearchQueryTest")
      excludeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
      excludeTestsMatching("OpenSearchQuerySanitizationDisabledJsonbTest")
    }
  }

  val testDisabledCaptureSearchQuery = register<Test>("testDisabledCaptureSearchQuery") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("OpenSearchDisabledCaptureSearchQueryTest")
    }
    jvmArgs("-Dotel.instrumentation.opensearch.capture-search-query=false")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.opensearch.capture-search-query=false",
    )
  }

  val testDeprecatedCaptureSearchQueryV3Preview =
    register<Test>("testDeprecatedCaptureSearchQueryV3Preview") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("OpenSearchCaptureSearchQueryTest")
      }
      jvmArgs(
        "-Dotel.instrumentation.opensearch.capture-search-query=false",
        "-Dotel.instrumentation.common.v3-preview=true",
      )
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.opensearch.capture-search-query=false,otel.instrumentation.common.v3-preview=true",
      )
    }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("OpenSearchDisabledCaptureSearchQueryTest")
      excludeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
      excludeTestsMatching("OpenSearchQuerySanitizationDisabledJsonbTest")
    }
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testQuerySanitizationDisabled = register<Test>("testQuerySanitizationDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
      includeTestsMatching("OpenSearchQuerySanitizationDisabledJsonbTest")
    }
    jvmArgs("-Dotel.instrumentation.opensearch.query-sanitization.enabled=false")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.opensearch.query-sanitization.enabled=false",
    )
  }

  val testQuerySanitizationEnabledOverride = register<Test>("testQuerySanitizationEnabledOverride") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("OpenSearchCaptureSearchQueryTest.shouldCaptureSearchQueryBody")
    }
    jvmArgs("-Dotel.instrumentation.common.db.query-sanitization.enabled=false")
    jvmArgs("-Dotel.instrumentation.opensearch.query-sanitization.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.db.query-sanitization.enabled=false,otel.instrumentation.opensearch.query-sanitization.enabled=true",
    )
  }

  val testQuerySanitizationDisabledStableSemconv =
    register<Test>("testQuerySanitizationDisabledStableSemconv") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("OpenSearchQuerySanitizationDisabledTest")
      }
      jvmArgs("-Dotel.instrumentation.common.db.query-sanitization.enabled=false")
      jvmArgs("-Dotel.semconv-stability.opt-in=database")
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.common.db.query-sanitization.enabled=false,otel.semconv-stability.opt-in=database",
      )
    }

  check {
    dependsOn(
      testStableSemconv,
      testDisabledCaptureSearchQuery,
      testDeprecatedCaptureSearchQueryV3Preview,
      testQuerySanitizationDisabled,
      testQuerySanitizationDisabledStableSemconv,
      testQuerySanitizationEnabledOverride,
    )
  }
}
