plugins {
  id("otel.bom-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom")

javaPlatform {
  allowDependencies()
}

val otelVersion: String by project

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom:${otelVersion}"))
}

otelBom.projectFilter.set { it.findProperty("otel.stable") == "true" }
