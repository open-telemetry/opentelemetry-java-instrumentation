plugins {
  id("otel.javaagent-bootstrap")
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:jdbc:library")
    output.dir(
      shadedDep.file("build/extracted/shadow-bootstrap"),
      "builtBy" to ":instrumentation:jdbc:library:extractShadowJarBootstrap"
    )
  }
}
