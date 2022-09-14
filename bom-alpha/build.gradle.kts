plugins {
  id("otel.bom-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials (Alpha)"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom-alpha")

javaPlatform {
  allowDependencies()
}

val otelVersion: String by project

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom:${otelVersion}"))
  api(platform("io.opentelemetry:opentelemetry-bom-alpha:${otelVersion}-alpha"))
  api(platform(project(":bom")))
}

otelBom.projectFilter.set { it.findProperty("otel.stable") != "true" }
