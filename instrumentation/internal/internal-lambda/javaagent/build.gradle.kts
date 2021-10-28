plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":javaagent-bootstrap"))
  implementation(project(":instrumentation:internal:internal-lambda-java9:javaagent"))

  testImplementation(project(":javaagent-bootstrap"))
}
