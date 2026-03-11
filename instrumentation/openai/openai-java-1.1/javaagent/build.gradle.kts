plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.openai")
    module.set("openai-java")
    versions.set("[1.1.0,3)")
    // TODO: assertInverse after completing instrumentation
  }
}

dependencies {
  implementation(project(":instrumentation:openai:openai-java-1.1:library"))

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    systemProperty("otel.instrumentation.genai.capture-message-content", "true")
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  val testExperimentalSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=gen_ai_latest_experimental")
  }

  check {
    dependsOn(testExperimentalSemconv)
  }
}
