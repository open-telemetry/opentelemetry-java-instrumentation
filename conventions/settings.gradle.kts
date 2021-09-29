includeBuild("../gradle-plugins") {
  dependencySubstitution {
    substitute(module("io.opentelemetry.instrumentation.muzzle-generation:io.opentelemetry.instrumentation.muzzle-generation.gradle.plugin")).using(project(":"))
    substitute(module("io.opentelemetry.instrumentation.muzzle-check:io.opentelemetry.instrumentation.muzzle-check.gradle.plugin")).using(project(":"))
  }
}