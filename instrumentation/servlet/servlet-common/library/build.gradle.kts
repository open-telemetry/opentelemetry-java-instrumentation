plugins {
  id("otel.library-instrumentation")
}

dependencies {
  implementation("org.slf4j:slf4j-api")
  compileOnly(project(":javaagent-instrumentation-api"))
}
