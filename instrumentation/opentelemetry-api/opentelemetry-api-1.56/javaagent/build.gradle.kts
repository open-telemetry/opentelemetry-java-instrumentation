plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "v1_56"))
  compileOnly("io.opentelemetry:opentelemetry-api-incubator")

  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.27:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.40:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.42:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.47:javaagent"))
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.50:javaagent"))

  testImplementation("io.opentelemetry:opentelemetry-api-incubator")
}

val testWithApi167 = otelProps.testLatestDeps || project.findProperty("testApi167") == "true"
val apiVersion = if (testWithApi167) "1.67.0" else "1.56.0"
val apiIncubatorVersion = if (testWithApi167) "1.67.0-alpha" else "1.56.0-alpha"

configurations.configureEach {
  if (name.endsWith("testRuntimeClasspath", true) || name.endsWith("testCompileClasspath", true)) {
    resolutionStrategy {
      force("io.opentelemetry:opentelemetry-api:$apiVersion")
      force("io.opentelemetry:opentelemetry-api-incubator:$apiIncubatorVersion")
    }
  }
}

tasks.test {
  jvmArgs(
    "-Dotel.config.file=$projectDir/src/test/resources/${if (testWithApi167) "declarative-config-1.67.yaml" else "declarative-config.yaml"}"
  )
}
