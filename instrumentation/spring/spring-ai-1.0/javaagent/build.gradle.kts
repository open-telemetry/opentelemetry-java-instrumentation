import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

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
  testInstrumentation(project(":instrumentation:http-url-connection:javaagent"))
  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  // The default suite uses the Spring AI 1.x OpenAI client API.
  latestDepTestLibrary("org.springframework.ai:spring-ai-model:1.+") // documented limitation
  latestDepTestLibrary("org.springframework.ai:spring-ai-openai:1.+") // documented limitation
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("CaptureMessageContent") {
          telemetryConfig("otel.instrumentation.genai.capture-message-content", "true")
        }
      }
    }
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("org.springframework.ai:spring-ai-model:1.0.0")
      }
    }
    register<JvmTestSuite>("version2Test") {
      dependencies {
        val springAiVersion = baseVersion("2.0.0").orLatest("2.+")
        implementation("org.springframework.ai:spring-ai-model:$springAiVersion")
        implementation("org.springframework.ai:spring-ai-openai:$springAiVersion")
        implementation(project(":instrumentation:reactor:reactor-3.1:library"))
      }
      executions {
        register("CaptureMessageContent") {
          telemetryConfig("otel.instrumentation.genai.capture-message-content", "true")
        }
      }
    }
  }
}

sourceSets.named("version2Test") {
  java.srcDir("src/test/java")
  // OpenAI client construction uses different APIs in Spring AI 1.x and 2.x.
  java.exclude("**/OpenAiChatModelTest.java")
  resources.srcDir("src/test/resources")
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
