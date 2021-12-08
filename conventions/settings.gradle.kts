includeBuild("../gradle-plugins") {
  dependencySubstitution {
    substitute(module("io.opentelemetry.instrumentation:gradle-plugins")).using(project(":"))
  }
}