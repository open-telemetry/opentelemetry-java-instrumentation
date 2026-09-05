plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly("org.elasticsearch.client:rest:5.0.0")

  bootstrap(project(":instrumentation:elasticsearch:elasticsearch-rest-common-5.0:bootstrap"))

  // the sanitizer runs in the agent class loader, where jackson-core already ships as a dependency
  // of :javaagent-tooling. It is declared here so that the dependency is explicit rather than
  // inherited by accident, and compileOnly so that it is not bundled a second time.
  compileOnly("com.fasterxml.jackson.core:jackson-core")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  api(project(":instrumentation:elasticsearch:elasticsearch-rest-common-5.0:library"))
}
