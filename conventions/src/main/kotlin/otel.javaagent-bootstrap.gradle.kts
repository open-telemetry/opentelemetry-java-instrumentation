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

  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":javaagent-instrumentation-api"))
}
