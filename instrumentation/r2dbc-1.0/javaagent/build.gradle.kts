
plugins {
  id("otel.javaagent-instrumentation")
  id("otel.java-conventions")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("io.r2dbc")
    module.set("r2dbc-spi")
    // SPI 0.9 matches the referenced API after type erasure, despite the Integer/Long
    // update-count difference. The supported and tested minimum is SPI 1.0.
    versions.set("[0.9.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.projectreactor:reactor-core:3.4.12")
  }
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:library-instrumentation-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:r2dbc-1.0:library-instrumentation-shaded:extractShadowJar",
    )
  }
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  compileOnly(project(path = ":instrumentation:r2dbc-1.0:library-instrumentation-shaded", configuration = "shadow"))

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}
