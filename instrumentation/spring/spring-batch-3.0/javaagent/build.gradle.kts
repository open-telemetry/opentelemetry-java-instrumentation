plugins {
  id("otel.javaagent-instrumentation")
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

tasks {
  val testChunkRootSpan by registering(Test::class) {
    filter {
      includeTestsMatching("*ChunkRootSpanTest")
    }
    include("**/*ChunkRootSpanTest.*")
    jvmArgs("-Dotel.instrumentation.spring-batch.experimental.chunk.new-trace=true")
  }

  val testItemLevelSpan by registering(Test::class) {
    filter {
      includeTestsMatching("*ItemLevelSpanTest")
      includeTestsMatching("*CustomSpanEventTest")
    }
    include("**/*ItemLevelSpanTest.*", "**/*CustomSpanEventTest.*")
    jvmArgs("-Dotel.instrumentation.spring-batch.item.enabled=true")
  }

  test {
    filter {
      excludeTestsMatching("*ChunkRootSpanTest")
      excludeTestsMatching("*ItemLevelSpanTest")
      excludeTestsMatching("*CustomSpanEventTest")
    }
  }

  check {
    dependsOn(testChunkRootSpan)
    dependsOn(testItemLevelSpan)
  }

  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    jvmArgs("-Dotel.instrumentation.spring-batch.enabled=true")
    // TODO run tests both with and without experimental span attributes
    jvmArgs("-Dotel.instrumentation.spring-batch.experimental-span-attributes=true")
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
