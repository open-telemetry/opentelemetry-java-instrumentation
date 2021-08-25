plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.batch")
    module.set("spring-batch-core")
    versions.set("[3.0.0.RELEASE,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.springframework.batch:spring-batch-core:3.0.0.RELEASE")

  testImplementation("javax.inject:javax.inject:1")
  // SimpleAsyncTaskExecutor context propagation
  testInstrumentation(project(":instrumentation:spring:spring-core-2.0:javaagent"))
}

tasks {
  val testChunkRootSpan by registering(Test::class) {
    filter {
      includeTestsMatching("*ChunkRootSpanTest")
      isFailOnNoMatchingTests = false
    }
    include("**/*ChunkRootSpanTest.*")
    jvmArgs("-Dotel.instrumentation.spring-batch.experimental.chunk.new-trace=true")
  }

  val testItemLevelSpan by registering(Test::class) {
    filter {
      includeTestsMatching("*ItemLevelSpanTest")
      includeTestsMatching("*CustomSpanEventTest")
      isFailOnNoMatchingTests = false
    }
    include("**/*ItemLevelSpanTest.*", "**/*CustomSpanEventTest.*")
    jvmArgs("-Dotel.instrumentation.spring-batch.item.enabled=true")
  }

  named<Test>("test") {
    dependsOn(testChunkRootSpan)
    dependsOn(testItemLevelSpan)
    filter {
      excludeTestsMatching("*ChunkRootSpanTest")
      excludeTestsMatching("*ItemLevelSpanTest")
      excludeTestsMatching("*CustomSpanEventTest")
      isFailOnNoMatchingTests = false
    }
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps"))
    jvmArgs("-Dotel.instrumentation.spring-batch.enabled=true")
  }
}
