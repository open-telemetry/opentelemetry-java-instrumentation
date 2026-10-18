plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

muzzle {
  pass {
    group.set("org.springframework.ai")
    module.set("spring-ai-model")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.ai:spring-ai-model:1.0.0")
  testLibrary("org.springframework.ai:spring-ai-openai:1.0.0")

  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))

  // current tests don't build with spring-ai 2
  latestDepTestLibrary("org.springframework.ai:spring-ai-model:1.+") // documented limitation
  latestDepTestLibrary("org.springframework.ai:spring-ai-openai:1.+") // documented limitation
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("org.springframework.ai:spring-ai-model:1.0.0")
      }
    }
    register<JvmTestSuite>("version2Test") {
      dependencies {
        val springAiVersion = if (otelProps.testLatestDeps) "2.+" else "2.0.0"
        implementation("org.springframework.ai:spring-ai-model:$springAiVersion")
        implementation("org.springframework.ai:spring-ai-openai:$springAiVersion")
      }
    }
  }
}

// version2Test also runs the shared test sources, excluding 1.x-only files
sourceSets.named("version2Test") {
  java.srcDir("src/test/java")
  // ChatModelTest uses ContextPropagationOperator and TestChatModel.getDefaultOptions() (removed in 2.x)
  // OpenAiChatModelTest and TestChatModel use APIs removed in spring-ai 2.x
  java.exclude(
    "**/ChatModelTest.java",
    "**/OpenAiChatModelTest.java",
    "**/app/TestChatModel.java",
  )
  resources.srcDir("src/test/resources")
}

tasks {
  named<Test>("version2Test") {
    filter {
      excludeTestsMatching("OpenAiChatModelTest")
    }
  }

  val testCaptureMessageContent = register<Test>("testCaptureMessageContent") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("otel.instrumentation.genai.capture-message-content", true)
    systemProperty("metadataConfig", "otel.instrumentation.genai.capture-message-content=true")
  }

  val version2TestCaptureMessageContent = register<Test>("version2TestCaptureMessageContent") {
    testClassesDirs = sourceSets.named("version2Test").get().output.classesDirs
    classpath = sourceSets.named("version2Test").get().runtimeClasspath

    systemProperty("otel.instrumentation.genai.capture-message-content", true)
    systemProperty("metadataConfig", "otel.instrumentation.genai.capture-message-content=true")
  }

  check {
    dependsOn(testing.suites, testCaptureMessageContent, version2TestCaptureMessageContent)
  }
}
