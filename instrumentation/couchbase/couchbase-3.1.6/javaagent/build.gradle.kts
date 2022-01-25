plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.couchbase.client")
    module.set("java-client")
    versions.set("[3.1.6,3.2.0)")
    // these versions were released as ".bundle" instead of ".jar"
    skip("2.7.5", "2.7.8")
    assertInverse.set(true)
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:couchbase:couchbase-3.1.6:tracing-opentelemetry-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:couchbase:couchbase-3.1.6:tracing-opentelemetry-shaded:extractShadowJar"
    )
  }
}

dependencies {
  compileOnly(
    project(
      path = ":instrumentation:couchbase:couchbase-3.1.6:tracing-opentelemetry-shaded",
      configuration = "shadow"
    )
  )

  library("com.couchbase.client:java-client:3.1.6")

  testImplementation("org.testcontainers:couchbase")

  latestDepTestLibrary("com.couchbase.client:java-client:3.1.+")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].getService())
  }
}
