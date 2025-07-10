plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.openai")
    module.set("openai-java")
    versions.set("[1.1.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:openai:openai-java-1.1:library"))

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  library("com.openai:openai-java:1.1.0")

  testImplementation(project(":instrumentation:openai:openai-java-1.1:testing"))

  // needed for latest dep tests
  testCompileOnly("com.google.errorprone:error_prone_annotations")
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
    // TODO run tests both with and without genai message capture
    systemProperty("otel.instrumentation.genai.capture-message-content", "true")
  }
}
