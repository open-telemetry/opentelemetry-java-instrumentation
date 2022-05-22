plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.azure")
    module.set("azure-core")
    versions.set("[1.14.0,1.19.0)")
    assertInverse.set(true)
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded")
    output.dir(shadedDep.file("build/extracted/shadow"), "builtBy" to ":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded:extractShadowJar")
  }
}

dependencies {
  compileOnly(project(":instrumentation:azure-core:azure-core-1.14:library-instrumentation-shaded", configuration = "shadow"))

  library("com.azure:azure-core:1.14.0")

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:azure-core:azure-core-1.19:javaagent"))

  latestDepTestLibrary("com.azure:azure-core:1.18.+") // see azure-core-1.19
}
