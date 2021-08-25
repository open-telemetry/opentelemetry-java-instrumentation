import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.InventoryMarkdownReportRenderer

plugins {
  id("com.github.jk1.dependency-license-report")

  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("io.opentelemetry.instrumentation.javaagent-shadowing")
}

description = "OpenTelemetry Javaagent"
group = "io.opentelemetry.javaagent"

// this configuration collects libs that will be placed in the bootstrap classloader
val bootstrapLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration collects libs that will be placed in the agent classloader, isolated from the instrumented application code
val javaagentLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration collects just exporter libs (also placed in the agent classloader & isolated from the instrumented application)
val exporterLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

// exclude dependencies that are to be placed in bootstrap from agent libs - they won't be added to inst/
listOf(javaagentLibs, exporterLibs).forEach {
  it.run {
    exclude("org.slf4j")
    exclude("io.opentelemetry", "opentelemetry-api")
    exclude("io.opentelemetry", "opentelemetry-api-metrics")
    exclude("io.opentelemetry", "opentelemetry-semconv")
  }
}

val licenseReportDependencies by configurations.creating {
  extendsFrom(bootstrapLibs)
}

dependencies {
  bootstrapLibs(project(":instrumentation-api"))
  bootstrapLibs(project(":instrumentation-api-annotation-support"))
  bootstrapLibs(project(":javaagent-bootstrap"))
  bootstrapLibs(project(":javaagent-instrumentation-api"))
  bootstrapLibs("org.slf4j:slf4j-simple")

  javaagentLibs(project(":javaagent-extension-api"))
  javaagentLibs(project(":javaagent-tooling"))
  javaagentLibs(project(":muzzle"))

  exporterLibs(project(":javaagent-exporters"))

  // We only have compileOnly dependencies on these to make sure they don't leak into POMs.
  licenseReportDependencies("com.github.ben-manes.caffeine:caffeine") {
    isTransitive = false
  }
  licenseReportDependencies("com.blogspot.mydailyjava:weak-lock-free")
  // TODO ideally this would be :instrumentation instead of :javaagent-tooling
  //  in case there are dependencies (accidentally) pulled in by instrumentation modules
  //  but I couldn"t get that to work
  licenseReportDependencies(project(":javaagent-tooling"))
  licenseReportDependencies(project(":javaagent-extension-api"))

  testCompileOnly(project(":javaagent-bootstrap"))
  testCompileOnly(project(":javaagent-instrumentation-api"))

  testImplementation("com.google.guava:guava")
  testImplementation("io.opentracing.contrib.dropwizard:dropwizard-opentracing:0.2.2")
}

val javaagentDependencies = dependencies

// collect all bootstrap and javaagent instrumentation dependencies
project(":instrumentation").subprojects {
  val subProj = this

  plugins.withId("otel.javaagent-bootstrap") {
    javaagentDependencies.run {
      add(bootstrapLibs.name, project(subProj.path))
    }
  }

  plugins.withId("otel.javaagent-instrumentation") {
    javaagentDependencies.run {
      add(javaagentLibs.name, project(subProj.path))
    }
  }
}

tasks {
  processResources {
    from(rootProject.file("licenses")) {
      into("META-INF/licenses")
    }
  }

  val relocateJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(javaagentLibs)

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveFileName.set("javaagentLibs-relocated.jar")

    // exclude bootstrap projects from javaagent libs - they won't be added to inst/
    dependencies {
      exclude(project(":instrumentation-api"))
      exclude(project(":instrumentation-api-annotation-support"))
      exclude(project(":javaagent-bootstrap"))
      exclude(project(":javaagent-instrumentation-api"))
    }
  }

  val relocateExporterLibs by registering(ShadowJar::class) {
    configurations = listOf(exporterLibs)

    archiveFileName.set("exporterLibs-relocated.jar")
  }

  // Includes instrumentations, but not exporters
  val shadowJar by existing(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateJavaagentLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)

    archiveClassifier.set("")

    manifest {
      attributes(jar.get().manifest.attributes)
      attributes(
        "Main-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Agent-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Premain-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
        "Can-Redefine-Classes" to true,
        "Can-Retransform-Classes" to true
      )
    }
  }

  // Includes everything needed for OOTB experience
  val fullJavaagentJar by registering(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateJavaagentLibs, relocateExporterLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)
    isolateClasses(relocateExporterLibs.get().outputs.files)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    archiveClassifier.set("all")

    manifest {
      attributes(shadowJar.get().manifest.attributes)
    }
  }

  assemble {
    dependsOn(shadowJar, fullJavaagentJar)
  }

  withType<Test>().configureEach {
    dependsOn(fullJavaagentJar)
    inputs.file(fullJavaagentJar.get().archiveFile)

    jvmArgs("-Dotel.javaagent.debug=true")

    doFirst {
      // Defining here to allow jacoco to be first on the command line.
      jvmArgs("-javaagent:${fullJavaagentJar.get().archiveFile.get().asFile}")
    }

    testLogging {
      events("started")
    }
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
        artifact(fullJavaagentJar)
      }
    }
  }
}

licenseReport {
  outputDir = rootProject.file("licenses").absolutePath

  renderers = arrayOf(InventoryMarkdownReportRenderer())

  configurations = arrayOf(licenseReportDependencies.name)

  excludeGroups = arrayOf(
    "io.opentelemetry.instrumentation",
    "io.opentelemetry.javaagent"
  )

  filters = arrayOf(LicenseBundleNormalizer("$projectDir/license-normalizer-bundle.json", true))
}

fun CopySpec.isolateClasses(jars: Iterable<File>) {
  jars.forEach {
    from(zipTree(it)) {
      // important to keep prefix "inst" short, as it is prefixed to lots of strings in runtime mem
      into("inst")
      rename("(^.*)\\.class\$", "\$1.classdata")
      // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
      rename("""^LICENSE$""", "LICENSE.renamed")
    }
  }
}
