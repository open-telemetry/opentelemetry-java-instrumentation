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

  // Get the semconv version from :dependencyManagement
  val semconvConstraint = project.project(project(":dependencyManagement").path).configurations["api"].allDependencyConstraints
    .find { it.group.equals("io.opentelemetry.semconv")
            && it.name.equals("opentelemetry-semconv-incubating") }
    ?: throw Exception("semconv constraint not found")
  val semconvAlphaVersion = semconvConstraint.version ?: throw Exception("missing version")
  otelBom.addExtra(semconvConstraint.group, "opentelemetry-semconv-incubating", semconvAlphaVersion)
}

otelBom.projectFilter.set { it.findProperty("otel.stable") != "true" }
