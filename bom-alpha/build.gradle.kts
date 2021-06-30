plugins {
  id("java-platform")

  id("otel.publish-conventions")
}

description = "OpenTelemetry Instrumentation Bill of Materials (Alpha)"
group = "io.opentelemetry.instrumentation"
base.archivesName.set("opentelemetry-instrumentation-bom-alpha")

for (subproject in rootProject.subprojects) {
  if (!subproject.name.startsWith("bom")) {
    evaluationDependsOn(subproject.path)
  }
}

javaPlatform {
  allowDependencies()
}

val otelVersion: String by project

dependencies {
  api(platform("io.opentelemetry:opentelemetry-bom:${otelVersion}"))
  api(platform("io.opentelemetry:opentelemetry-bom-alpha:${otelVersion}-alpha"))
}

afterEvaluate {
  dependencies {
    constraints {
      rootProject.subprojects
        .filter { it.name != project.name && it.name != "javaagent" }
        .forEach { proj ->
          proj.plugins.withId("maven-publish") {
            api(proj)
          }
        }
    }
  }
}
