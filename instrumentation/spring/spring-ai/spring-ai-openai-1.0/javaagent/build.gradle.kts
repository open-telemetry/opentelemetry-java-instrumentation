plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // Spring AI OpenAI requires java 17 (same as Spring AI)
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

muzzle {
  pass {
    group.set("org.springframework.ai")
    module.set("spring-ai-openai")
    versions.set("(,)")
  }
}

repositories {
  mavenLocal()
  maven {
    url = uri("https://repo.spring.io/milestone")
    content {
      includeGroup("org.springframework.ai")
      includeGroup("org.springframework.boot")
      includeGroup("org.springframework")
    }
  }
  maven {
    url = uri("https://repo.spring.io/snapshot")
    content {
      includeGroup("org.springframework.ai")
      includeGroup("org.springframework.boot")
      includeGroup("org.springframework")
    }
    mavenContent {
      snapshotsOnly()
    }
  }
  mavenCentral()
}

dependencies {
  library("io.projectreactor:reactor-core:3.7.0")
  library("org.springframework.ai:spring-ai-openai:1.0.0")
  library("org.springframework.ai:spring-ai-model:1.0.0")

  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  bootstrap(project(":instrumentation:reactor:reactor-3.1:bootstrap"))

  testImplementation(project(":instrumentation:spring:spring-ai:spring-ai-openai-1.0:testing"))
}

tasks {
  withType<Test>().configureEach {
    val latestDepTest = findProperty("testLatestDeps") as Boolean
    systemProperty("testLatestDeps", latestDepTest)
    // spring ai requires java 17
    if (latestDepTest) {
      otelJava {
        minJavaVersionSupported.set(JavaVersion.VERSION_17)
      }
    }

    // TODO run tests both with and without genai message capture
    systemProperty("otel.instrumentation.genai.capture-message-content", "true")
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }
}
