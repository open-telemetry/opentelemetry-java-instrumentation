plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[20,)")
    skip("230521-nf-execution")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-20.0:library"))
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  library("com.graphql-java:graphql-java:20.0")

  testInstrumentation(project(":instrumentation:graphql-java:graphql-java-12.0:javaagent"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs(
      "-Dotel.instrumentation.graphql.operation-name-in-span-name.enabled=true",
      "-Dotel.instrumentation.graphql.add-operation-name-to-span-name.enabled=false",
    )
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testDataFetcher = register<Test>("testDataFetcher") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.graphql.data-fetcher.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.graphql.data-fetcher.enabled=true")
  }

  val testDeprecatedCaptureQueryDisabled = register<Test>("testDeprecatedCaptureQueryDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      includeTestsMatching("GraphqlTest")
    }
    jvmArgs("-Dotel.instrumentation.graphql.capture-query=false")
    systemProperty("metadataConfig", "otel.instrumentation.graphql.capture-query=false")
  }

  val testDeprecatedCaptureQueryV3Preview =
    register<Test>("testDeprecatedCaptureQueryV3Preview") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("GraphqlTest")
      }
      jvmArgs(
        "-Dotel.instrumentation.graphql.capture-query=false",
        "-Dotel.instrumentation.common.v3-preview=true",
      )
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.graphql.capture-query=false,otel.instrumentation.common.v3-preview=true",
      )
    }

  check {
    dependsOn(
      testDataFetcher,
      testDeprecatedCaptureQueryDisabled,
      testDeprecatedCaptureQueryV3Preview,
    )
  }
}

if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
}
