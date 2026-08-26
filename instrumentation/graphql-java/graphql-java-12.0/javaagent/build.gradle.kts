plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.graphql-java")
    module.set("graphql-java")
    versions.set("[12,20)")
    skip("230521-nf-execution")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:graphql-java:graphql-java-12.0:library"))
  implementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:library"))

  library("com.graphql-java:graphql-java:12.0")

  testInstrumentation(project(":instrumentation:graphql-java:graphql-java-20.0:javaagent"))

  testImplementation(project(":instrumentation:graphql-java:graphql-java-common-12.0:testing"))

  latestDepTestLibrary("com.graphql-java:graphql-java:19.+") // see graphql-java-20.0 module
}

tasks {
  withType<Test>().configureEach {
    jvmArgs(
      "-Dotel.instrumentation.graphql.operation-name-in-span-name.enabled=true",
      "-Dotel.instrumentation.graphql.add-operation-name-to-span-name.enabled=false",
    )

    systemProperty("collectMetadata", otelProps.collectMetadata)
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
    dependsOn(testDeprecatedCaptureQueryDisabled, testDeprecatedCaptureQueryV3Preview)
  }
}
