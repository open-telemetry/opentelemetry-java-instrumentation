plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))
  implementation(project(":instrumentation:internal:internal-lambda-java9:javaagent"))

  testImplementation(project(":javaagent-bootstrap"))
}

// disable muzzle codegen - this module includes java9+ only classes, and they must not be captured as references or helpers
tasks.named("byteBuddy").configure {
  enabled = false
}
