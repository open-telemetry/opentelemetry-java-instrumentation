// SDK extensions are very similar to library instrumentations, they can be used without the javaagent
// but since they depend on the SDK they must be loaded by the agent CL in the javaagent

plugins {
  id("io.opentelemetry.instrumentation.library-instrumentation")

  id("otel.jacoco-conventions")
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.instrumentation"

base.archivesName.set(projectDir.parentFile.name)