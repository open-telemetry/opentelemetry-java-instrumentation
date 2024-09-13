plugins {
  id("com.gradleup.shadow")
  id("otel.java-conventions")
}

group = "io.opentelemetry.javaagent.instrumentation"

dependencies {
  implementation(project(":instrumentation:r2dbc-1.0:library"))
}

tasks {
  shadowJar {
    exclude {
      it.path.startsWith("META-INF") && !it.path.startsWith("META-INF/io/opentelemetry/instrumentation/")
    }

    dependencies {
      // including only :r2dbc-1.0:library excludes its transitive dependencies
      include(project(":instrumentation:r2dbc-1.0:library"))
      include(dependency("io.r2dbc:r2dbc-proxy"))
    }
    relocate(
      "io.r2dbc.proxy",
      "io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded.io.r2dbc.proxy"
    )
    relocate(
      "io.opentelemetry.instrumentation.r2dbc.v1_0",
      "io.opentelemetry.instrumentation.r2dbc.v1_0.internal.shaded"
    )
  }

  val extractShadowJar by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    exclude("META-INF/**")
    into("build/extracted/shadow")
  }

  val extractShadowJarSpring by registering(Copy::class) {
    dependsOn(shadowJar)
    from(zipTree(shadowJar.get().archiveFile))
    into("build/extracted/shadow-spring")
  }
}
