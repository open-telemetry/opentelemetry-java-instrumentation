plugins {
  id("otel.library-instrumentation")
}

sourceSets {
  main {
    val shadedDep = project(":instrumentation:r2dbc-1.0:tracing-opentelemetry-shaded")
    output.dir(
      shadedDep.file("build/extracted/shadow"),
      "builtBy" to ":instrumentation:r2dbc-1.0:tracing-opentelemetry-shaded:extractShadowJar",
    )
  }
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  api(
    project(
      path = ":instrumentation:r2dbc-1.0:tracing-opentelemetry-shaded",
      configuration = "shadow",
    ),
  )

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}
