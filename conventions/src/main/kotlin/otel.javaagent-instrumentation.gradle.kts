plugins {
  id("otel.javaagent-testing")
  id("otel.publish-conventions")

  id("io.opentelemetry.instrumentation.javaagent-instrumentation")
}

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"

base.archivesName.set(projectDir.parentFile.name)