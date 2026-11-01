import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.batch")
    module.set("spring-batch-core")
    versions.set("[3.0.0.RELEASE,5)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.batch:spring-batch-core:3.0.0.RELEASE")

  testImplementation("javax.inject:javax.inject:1")

  // SimpleAsyncTaskExecutor context propagation
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))

  // spring batch 5.0 uses spring framework 6.0
  latestDepTestLibrary("org.springframework.batch:spring-batch-core:4.+") // documented limitation
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    configureEach {
      // required on jdk17
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

      systemProperty("testLatestDeps", otelProps.testLatestDeps)
      jvmArgs("-Dotel.instrumentation.spring-batch.enabled=true")
    }

    configureDefault {
      filter {
        excludeTestsMatching("*ChunkRootSpanTest")
        excludeTestsMatching("*ItemLevelSpanTest")
        excludeTestsMatching("*CustomSpanEventTest")
      }
    }

    register("ChunkRootSpan") {
      filter {
        includeTestsMatching("*ChunkRootSpanTest")
      }
      include("**/*ChunkRootSpanTest.*")
      telemetryConfig("otel.instrumentation.spring-batch.experimental.chunk.new-trace", "true")
    }

    register("ItemLevelSpan") {
      filter {
        includeTestsMatching("*ItemLevelSpanTest")
        includeTestsMatching("*CustomSpanEventTest")
      }
      include("**/*ItemLevelSpanTest.*", "**/*CustomSpanEventTest.*")
      telemetryConfig("otel.instrumentation.spring-batch.item.enabled", "true")
    }

    register("Experimental") {
      filter {
        excludeTestsMatching("*ChunkRootSpanTest")
        excludeTestsMatching("*ItemLevelSpanTest")
        excludeTestsMatching("*CustomSpanEventTest")
      }
      telemetryConfig("otel.instrumentation.spring-batch.experimental-span-attributes", "true")
    }
  }
}
