plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"

// make sure that archive name contains the instrumentation lib name
base.archivesName.set(projectDir.parentFile.name + "-bootstrap")

dependencies {
  compileOnly("io.opentelemetry:opentelemetry-api")
  compileOnly(project(":instrumentation-api"))
  compileOnly(project(":javaagent-instrumentation-api"))

  // this only exists to make Intellij happy since it doesn't (currently at least) understand our
  // inclusion of this artifact inside of :instrumentation-api
//  compileOnly(project(":instrumentation-api-caching"))
}
