plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
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
