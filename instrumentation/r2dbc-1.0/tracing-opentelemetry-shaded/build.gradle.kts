plugins {
  id("com.github.johnrengelman.shadow")

  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  implementation("io.r2dbc:r2dbc-proxy:1.0.1.RELEASE")
}

tasks {
  shadowJar {
    dependencies {
      // including only tracing-opentelemetry excludes its transitive dependencies
      include(dependency("io.r2dbc:r2dbc-proxy"))
    }
    relocate(
      "io.r2dbc.proxy",
      "io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.shaded.io.r2dbc.proxy"
    )
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow")
  }
}
