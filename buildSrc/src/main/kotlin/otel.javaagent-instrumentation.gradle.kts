plugins {
  id("otel.javaagent-testing")
  id("otel.publish-conventions")
}

// TODO(anuraaga): Migrate MuzzlePlugin to kotlin script since Java plugins don't seem to be usable" +
// with plugins block. It will be good to add a property for bootstrapRuntime to that plugin at the
// time instead of defining it here and reading it in a non-obvious way.
apply(plugin = "muzzle")

extra["mavenGroupId"] = "io.opentelemetry.javaagent.instrumentation"

base.archivesName.set(projectDir.parentFile.name)

// Used by muzzle
val bootstrapRuntime by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
}

dependencies {
  bootstrapRuntime(project(path = ":javaagent-bootstrap", configuration = "instrumentationMuzzle"))
}
