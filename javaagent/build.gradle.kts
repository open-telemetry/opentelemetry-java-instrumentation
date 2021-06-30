import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer

plugins {
  id("com.github.jk1.dependency-license-report")

  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.shadow-conventions")
}

description = "OpenTelemetry Javaagent"

group = "io.opentelemetry.javaagent"

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

fun isolateSpec(projectsWithShadowJar: Collection<Project>): CopySpec = copySpec {
  from(projectsWithShadowJar.map { zipTree(it.tasks.getByName<ShadowJar>("shadowJar").archiveFile) }) {
    // important to keep prefix "inst" short, as it is prefixed to lots of strings in runtime mem
    into("inst")
    rename("""(^.*)\.class$""", "$1.classdata")
    // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
    rename("""^LICENSE$""", "LICENSE.renamed")
  }
}

tasks {
  processResources.configure {
    from(rootProject.file("licenses")) {
      into("META-INF/licenses")
    }
  }

  //Includes everything needed for OOTB experience
  val shadowJar by existing(ShadowJar::class) {
    archiveClassifier.set("all")
    val projectsWithShadowJar = listOf(project(":instrumentation"), project(":javaagent-exporters"))
    projectsWithShadowJar.forEach {
      dependsOn("${it.path}:shadowJar")
    }
    with(isolateSpec(projectsWithShadowJar))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
  }

  //Includes instrumentations, but not exporters
  val lightShadow by registering(ShadowJar::class) {
    archiveClassifier.set("")
    dependsOn(":instrumentation:shadowJar")
    val projectsWithShadowJar = listOf(project(":instrumentation"))
    with(isolateSpec(projectsWithShadowJar))
  }

  // lightShadow is the default classifier we publish so disable the default jar.
  jar.configure {
    enabled = false

    manifest {
      attributes(
        "Main-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Agent-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Premain-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Can-Redefine-Classes" to true,
        "Can-Retransform-Classes" to true
      )
    }
  }

  withType<ShadowJar>().configureEach {
    configurations = listOf(shadowInclude)

    manifest.inheritFrom(jar.get().manifest)
  }

  withType<Test>().configureEach {
    inputs.file(shadowJar.get().archiveFile)

    jvmArgs("-Dotel.javaagent.debug=true")

    doFirst {
      // Defining here to allow jacoco to be first on the command line.
      jvmArgs("-javaagent:${shadowJar.get().archivePath}")
    }

    testLogging {
      events("started")
    }

    dependsOn(shadowJar)
  }

  named("assemble") {
    dependsOn(lightShadow)
    dependsOn(shadowJar)
  }

  val cleanLicenses by registering(Delete::class) {
    delete(rootProject.file("licenses"))
  }

  named("generateLicenseReport").configure {
    dependsOn(cleanLicenses)
  }

  publishing {
    publications {
      named<MavenPublication>("maven") {
        artifact(lightShadow)
      }
    }
  }
}

val licenseReportDependencies by configurations.creating

dependencies {
  testCompileOnly(project(":javaagent-bootstrap"))
  testCompileOnly(project(":javaagent-api"))

  testImplementation("com.google.guava:guava")

  testImplementation("io.opentracing.contrib.dropwizard:dropwizard-opentracing:0.2.2")

  shadowInclude(project(":javaagent-bootstrap"))

  // We only have compileOnly dependencies on these to make sure they don"t leak into POMs.
  licenseReportDependencies("com.github.ben-manes.caffeine:caffeine") {
    isTransitive = false
  }
  licenseReportDependencies("com.blogspot.mydailyjava:weak-lock-free")
  // TODO ideally this would be :instrumentation instead of :javaagent-tooling
  //  in case there are dependencies (accidentally) pulled in by instrumentation modules
  //  but I couldn"t get that to work
  licenseReportDependencies(project(":javaagent-tooling"))
  licenseReportDependencies(project(":javaagent-extension-api"))
  licenseReportDependencies(project(":javaagent-bootstrap"))
}


licenseReport {
  outputDir = rootProject.file("licenses").absolutePath

  renderers = arrayOf(InventoryMarkdownReportRenderer())

  configurations = arrayOf("licenseReportDependencies")

  excludeGroups = arrayOf(
    "io.opentelemetry.instrumentation",
    "io.opentelemetry.javaagent"
  )

  filters = arrayOf(LicenseBundleNormalizer("$projectDir/license-normalizer-bundle.json", true))
}
