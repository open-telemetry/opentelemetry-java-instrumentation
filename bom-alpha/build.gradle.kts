plugins {
  id("otel.bom-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials (Alpha)"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom-alpha")

javaPlatform {
  allowDependencies()
}

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom"))
  api(platform("io.opentelemetry:opentelemetry-bom-alpha"))
  api(platform(project(":bom")))
}

otelBom.projectFilter.set { it.findProperty("otel.stable") != "true" }
