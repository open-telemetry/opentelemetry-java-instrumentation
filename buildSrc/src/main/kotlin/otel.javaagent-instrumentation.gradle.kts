plugins {
  id("otel.javaagent-testing")
  id("otel.publish-conventions")
  id("otel.muzzle-check")
}

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"

base.archivesName.set(projectDir.parentFile.name)

dependencies {
  add("muzzleBootstrap", project(path = ":javaagent-bootstrap", configuration = "instrumentationMuzzle"))

  add("muzzleTooling", project(path = ":javaagent-tooling", configuration = "instrumentationMuzzle"))
  add("muzzleTooling", project(path = ":javaagent-extension-api", configuration = "instrumentationMuzzle"))
}
