/*
 * Classes that are common to all versions of the Hibernate instrumentation.
 */
plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly(project(":javaagent-instrumentation-api"))
}
