plugins {
  id("otel.java-conventions")
  id("otel.jacoco-conventions")
  id("otel.publish-conventions")
  id("otel.instrumentation-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.instrumentation"

base.archivesName.set(projectDir.parentFile.name)

dependencies {
  api(project(":instrumentation-api"))

  api("io.opentelemetry:opentelemetry-api")

  testImplementation(project(":testing-common"))
}
