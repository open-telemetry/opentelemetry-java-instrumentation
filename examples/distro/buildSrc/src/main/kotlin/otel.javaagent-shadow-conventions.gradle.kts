import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
  id("com.gradleup.shadow")
}

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
// this configuration stores the upstream agent dep that's extended by this project
val upstreamAgent by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

fun isolateClasses(jars: Iterable<File>): CopySpec = copySpec {
  jars.forEach {
    from(zipTree(it)) {
      into("inst")
      rename("^(.*)\\.class\$", "\$1.classdata")
      exclude("^LICENSE\$")
      exclude("META-INF/INDEX.LIST")
      exclude("META-INF/*.DSA")
      exclude("META-INF/*.SF")
      exclude("META-INF/maven/**")
      exclude("META-INF/MANIFEST.MF")
    }
  }
}

tasks {
  jar {
    enabled = false
  }

  // building the final javaagent jar is done in 3 steps:

  // 1. all distro specific javaagent libs are relocated
  val relocateJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(javaagentLibs)

    archiveFileName.set("javaagentLibs-relocated.jar")

    duplicatesStrategy = DuplicatesStrategy.FAIL
    mergeServiceFiles()
    // mergeServiceFiles requires that duplicate strategy is set to include
    filesMatching("META-INF/services/**") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    exclude("**/module-info.class")
    relocatePackages(this)

    // exclude known bootstrap dependencies - they can't appear in the inst/ directory
    dependencies {
      exclude(dependency("io.opentelemetry:opentelemetry-api"))
      exclude(dependency("io.opentelemetry:opentelemetry-common"))
      exclude(dependency("io.opentelemetry:opentelemetry-context"))
      exclude(dependency("io.opentelemetry.semconv:opentelemetry-semconv"))
      exclude(dependency("io.opentelemetry.semconv:opentelemetry-semconv-incubating"))
      // events API and metrics advice API
      exclude(dependency("io.opentelemetry:opentelemetry-api-incubator"))
    }
  }

  // 2. the distro javaagent libs are then isolated - moved to the inst/ directory
  // having a separate task for isolating javaagent libs is required to avoid duplicates with the upstream javaagent
  // duplicatesStrategy in shadowJar won't be applied when adding files with with(CopySpec) because each CopySpec has
  // its own duplicatesStrategy
  val isolateJavaagentLibs by registering(Copy::class) {
    dependsOn(relocateJavaagentLibs)
    with(isolateClasses(relocateJavaagentLibs.get().outputs.files))

    into(layout.buildDirectory.dir("isolated/javaagentLibs"))
  }

  // 3. the relocated and isolated javaagent libs are merged together with the bootstrap libs (which undergo relocation
  // in this task) and the upstream javaagent jar; duplicates are removed
  shadowJar {
    configurations = listOf(bootstrapLibs, upstreamAgent)

    from(isolateJavaagentLibs)

    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles("inst/META-INF/services")
    // mergeServiceFiles requires that duplicate strategy is set to include
    filesMatching("inst/META-INF/services/**") {
      duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    exclude("**/module-info.class")
    relocatePackages(this)

    manifest {
      attributes["Main-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Agent-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Premain-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Can-Redefine-Classes"] = "true"
      attributes["Can-Retransform-Classes"] = "true"
      attributes["Implementation-Vendor"] = "Demo"
      attributes["Implementation-Version"] = "demo-${project.version}-otel-$opentelemetryJavaagentVersion"
    }
  }

  assemble {
    dependsOn(shadowJar)
  }
}
