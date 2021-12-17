plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  library("org.apache.logging.log4j:log4j-core:2.16.0")

  testImplementation(project(":instrumentation:log4j:log4j-2-common:testing"))
}
