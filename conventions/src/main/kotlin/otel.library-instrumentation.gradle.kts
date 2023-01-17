plugins {
  id("io.opentelemetry.instrumentation.library-instrumentation")

  id("otel.jacoco-conventions")
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.instrumentation"

base.archivesName.set(projectDir.parentFile.name)