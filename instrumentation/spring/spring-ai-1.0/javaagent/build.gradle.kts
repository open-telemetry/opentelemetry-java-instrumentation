plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

muzzle {
  pass {
    group.set("org.springframework.ai")
    module.set("spring-ai-model")
    versions.set("[1.0.0,2)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.ai:spring-ai-model:1.0.0")
  testLibrary("org.springframework.ai:spring-ai-openai:1.0.0")
  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    systemProperty("otel.instrumentation.genai.capture-message-content", true)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs(
      "-Dotel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled=true"
    )
    systemProperty("otel.instrumentation.genai.capture-message-content", false)
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled=true",
    )
  }

  val testExperimentalWithSmallMessageContentLimit =
    register<Test>("testExperimentalWithSmallMessageContentLimit") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath

      filter {
        includeTestsMatching("*ChatModelTest.messageSpanAttributeUsesConfiguredMaxLength")
        includeTestsMatching("*ChatModelTest.streamedMessageSpanAttributeUsesConfiguredMaxLength")
        includeTestsMatching("*ChatModelTest.streamedToolCallArgumentsUseConfiguredMaxLength")
      }
      jvmArgs(
        "-Dotel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled=true",
        "-Dotel.instrumentation.spring-ai.experimental.message-content-span-attribute.max-length=10",
      )
      systemProperty("otel.instrumentation.genai.capture-message-content", false)
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.spring-ai.experimental.capture-message-content-as-span-attributes.enabled=true,otel.instrumentation.spring-ai.experimental.message-content-span-attribute.max-length=10",
      )
    }

  val testContentDisabled = register<Test>("testContentDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("otel.instrumentation.genai.capture-message-content", false)
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.genai.capture-message-content=false",
    )
  }

  check {
    dependsOn(testExperimental, testExperimentalWithSmallMessageContentLimit, testContentDisabled)
  }
}
