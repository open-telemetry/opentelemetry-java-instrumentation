import io.opentelemetry.instrumentation.gradle.OtelBomExtension

plugins {
  id("otel.publish-conventions")
  id("java-platform")
}

if (!project.name.startsWith("bom")) {
  throw IllegalStateException("Name of BOM projects must start with 'bom'.")
}

rootProject.subprojects.forEach { subproject ->
  if (!subproject.name.startsWith("bom")) {
    evaluationDependsOn(subproject.path)
  }
}
val otelBom = extensions.create<OtelBomExtension>("otelBom")

afterEvaluate {
  otelBom.projectFilter.finalizeValue()
  val bomProjects = rootProject.subprojects
    .sortedBy { it.findProperty("archivesName") as String? }
    .filter { !it.name.startsWith("bom") }
    .filter(otelBom.projectFilter.get()::test)
    .filter { it.plugins.hasPlugin("maven-publish") }

  bomProjects.forEach { project ->
    dependencies {
      constraints {
        api(project)
      }
    }
  }
  otelBom.additionalDependencies.forEach { dependency ->
    dependencies {
      constraints {
        api(dependency)
      }
    }
  }
}

// this applies version numbers to the SDK bom and SDK alpha bom which are dependencies of the instrumentation boms
evaluationDependsOn(":dependencyManagement")
val dependencyManagementConf = configurations.create("dependencyManagement") {
  isCanBeConsumed = false
  isCanBeResolved = false
  isVisible = false
}
afterEvaluate {
  configurations.configureEach {
    if (isCanBeResolved && !isCanBeConsumed) {
      extendsFrom(dependencyManagementConf)
    }
  }
}

dependencies {
  add(dependencyManagementConf.name, platform(project(":dependencyManagement")))
}
