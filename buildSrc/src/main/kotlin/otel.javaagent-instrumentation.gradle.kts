plugins {
  id("otel.javaagent-testing")
  id("otel.publish-conventions")

  id("io.opentelemetry.instrumentation.javaagent-instrumentation")
}

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"

base.archivesName.set(projectDir.parentFile.name)

dependencies {
  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
  compileOnly(project(":instrumentation-api-caching"))
}
