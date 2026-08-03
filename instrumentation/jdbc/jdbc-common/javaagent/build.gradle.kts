/*
 * Contains the shaded JDBC library classes shared by JDBC-based javaagent instrumentations.
 */
plugins {
  id("otel.javaagent-instrumentation")
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:jdbc:library")
    output.dir(
      shadedDep.file("build/extracted/shadow-javaagent"),
      "builtBy" to ":instrumentation:jdbc:library:extractShadowJarJavaagent",
    )
  }
}
