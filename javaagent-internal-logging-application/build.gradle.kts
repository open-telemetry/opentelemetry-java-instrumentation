plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.javaagent"

dependencies {
  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":javaagent-bootstrap"))
  compileOnly(project(":javaagent-tooling"))

  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":javaagent-bootstrap"))
  testImplementation(project(":javaagent-tooling"))

  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")
  testCompileOnly("com.google.auto.service:auto-service-annotations")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
