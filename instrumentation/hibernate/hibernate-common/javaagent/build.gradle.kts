/*
 * Classes that are common to all versions of the Hibernate instrumentation.
 */
plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly(project(":javaagent-extension-api"))
}
