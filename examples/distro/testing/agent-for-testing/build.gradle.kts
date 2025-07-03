plugins {
  id("otel.java-conventions")
  id("com.gradleup.shadow")
  id("otel.shadow-conventions")
}

configurations {
  // this configuration collects libs that will be placed in the bootstrap classloader
  create("bootstrapLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
  }
  // this configuration collects libs that will be placed in the agent classloader, isolated from the instrumented application code
  create("javaagentLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
  }
  // this configuration stores the upstream agent dep that's extended by this project
  create("upstreamAgent") {
    isCanBeResolved = true
    isCanBeConsumed = false
  }
}

dependencies {
  "bootstrapLibs"(project(":bootstrap"))
  // and finally include everything from otel agent for testing
  "upstreamAgent"("io.opentelemetry.javaagent:opentelemetry-agent-for-testing:${rootProject.extra["otelInstrumentationAlphaVersion"]}")
}

fun CopySpec.isolateClasses(jars: Iterable<File>): CopySpec {
  return copySpec {
    jars.forEach {
      from(zipTree(it)) {
        into("inst")
        rename("^(.*)\\.class\$", "\$1.classdata")
        // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
        rename("^LICENSE\$", "LICENSE.renamed")
        exclude("META-INF/INDEX.LIST")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.SF")
      }
    }
  }
}

tasks {
  jar {
    enabled = false
  }

  // building the final javaagent jar is done in 3 steps:

  // 1. all distro specific javaagent libs are relocated
  val relocateJavaagentLibs by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class) {
    configurations = listOf(project.configurations["javaagentLibs"])

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveFileName.set("javaagentLibs-relocated.jar")

    mergeServiceFiles()
    exclude("**/module-info.class")

    // exclude known bootstrap dependencies - they can't appear in the inst/ directory
    dependencies {
      exclude("io.opentelemetry:opentelemetry-api")
      exclude("io.opentelemetry:opentelemetry-context")
      // events API and metrics advice API
      exclude("io.opentelemetry:opentelemetry-api-incubator")
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
    configurations = listOf(project.configurations["bootstrapLibs"], project.configurations["upstreamAgent"])

    dependsOn(isolateJavaagentLibs)
    from(isolateJavaagentLibs.get().outputs)

    archiveClassifier.set("")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles {
      include("inst/META-INF/services/*")
    }
    exclude("**/module-info.class")

    manifest {
      attributes["Main-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Agent-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Premain-Class"] = "io.opentelemetry.javaagent.OpenTelemetryAgent"
      attributes["Can-Redefine-Classes"] = "true"
      attributes["Can-Retransform-Classes"] = "true"
      attributes["Implementation-Vendor"] = "Demo"
      attributes["Implementation-Version"] = "demo-${project.version}-otel-${rootProject.extra["otelInstrumentationVersion"]}"
    }
  }

  assemble {
    dependsOn(shadowJar)
  }
} 