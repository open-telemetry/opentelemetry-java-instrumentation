plugins {
  id("otel.javaagent-instrumentation")
  id("otel.java-conventions")
}

muzzle {

  pass {
    group.set("io.r2dbc")
    module.set("r2dbc-spi")
    versions.set("[1.0.0.RELEASE,)")
  }
}

dependencies {
  library("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  implementation(project(":instrumentation:r2dbc-1.0:library"))

  testImplementation(project(":instrumentation:r2dbc-1.0:testing"))
  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
}
