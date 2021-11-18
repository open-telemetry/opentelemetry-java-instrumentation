plugins {
  id("io.opentelemetry.instrumentation.library-instrumentation")

  id("otel.jacoco-conventions")
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.instrumentation"

base.archivesName.set(projectDir.parentFile.name)

dependencies {
  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
//  compileOnly(project(":instrumentation-api-caching"))
}
