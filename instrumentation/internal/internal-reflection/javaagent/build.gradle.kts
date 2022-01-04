plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  bootstrap(project(":instrumentation:internal:internal-reflection:bootstrap"))

  compileOnly(project(":javaagent-bootstrap"))

  testImplementation(project(":javaagent-bootstrap"))
}
