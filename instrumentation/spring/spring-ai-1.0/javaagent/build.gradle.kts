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
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testCaptureMessageContent = register<Test>("testCaptureMessageContent") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("otel.instrumentation.genai.capture-message-content", true)
    systemProperty("metadataConfig", "otel.instrumentation.genai.capture-message-content=true")
  }

  check {
    dependsOn(testing.suites, testCaptureMessageContent)
  }
}
