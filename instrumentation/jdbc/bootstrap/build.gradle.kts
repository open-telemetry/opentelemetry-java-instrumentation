plugins {
  id("otel.javaagent-bootstrap")
}

/*
JDBC instrumentation uses VirtualField<Connection, DbInfo>. Add DbInfo, that is used as the value of
VirtualField, to boot loader. We do this because when JDBC instrumentation is started in multiple
class loaders in the same hierarchy, each would define their own version of DbInfo. It is possible
that the value read from virtual field would be from the wrong class loader and could produce a
ClassCastException. Having a single copy of DbInfo that is in boot loader avoids this issue.
 */

sourceSets {
  main {
    val shadedDep = project(":instrumentation:jdbc:library")
    output.dir(
      shadedDep.file("build/extracted/shadow-bootstrap"),
      "builtBy" to ":instrumentation:jdbc:library:extractShadowJarBootstrap",
    )
  }
}
