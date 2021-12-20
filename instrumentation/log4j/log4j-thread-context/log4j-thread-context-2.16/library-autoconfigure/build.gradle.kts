plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("log4j-thread-context-autoconfigure-2.16")

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.16.0")

  testImplementation(project(":instrumentation:log4j:log4j-thread-context:log4j-thread-context-2-common:testing"))
}
