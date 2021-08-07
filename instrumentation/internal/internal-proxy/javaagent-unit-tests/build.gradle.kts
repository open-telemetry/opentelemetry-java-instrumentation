plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:internal:internal-proxy:javaagent"))
  testImplementation(project(":javaagent-bootstrap"))
}
