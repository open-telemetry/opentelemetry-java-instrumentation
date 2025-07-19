plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.openai")
    module.set("openai-java")
    versions.set("[1.1.0,)")
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
    // TODO run tests both with and without genai message capture
    systemProperty("otel.instrumentation.genai.capture-message-content", "true")
  }
}
