plugins {
  id("java-platform")

  id("otel.publish-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials (Alpha)"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom-alpha")

javaPlatform {
  allowDependencies()
}

val otelVersion: String by project
val otelAlphaVersion: String by project

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom:${otelVersion}"))
  api(platform("io.opentelemetry:opentelemetry-bom-alpha:${otelAlphaVersion}"))
}

dependencies {
  constraints {
    rootProject.subprojects {
      val proj = this
      if (!proj.name.startsWith("bom") && proj.name != "javaagent") {
        proj.plugins.withId("maven-publish") {
          api(proj)
        }
      }
    }
  }
}
