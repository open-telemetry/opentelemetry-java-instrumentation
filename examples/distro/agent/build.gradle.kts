import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
  id("otel.shadow-conventions")
}

base.archivesName.set("otel-javaagent")

java {
  withJavadocJar()
  withSourcesJar()
}

// this configuration collects libs that will be placed in the bootstrap classloader
val bootstrapLibs: Configuration by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration collects libs that will be placed in the agent classloader, isolated from the instrumented application code
val javaagentLibs: Configuration by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
// this configuration stores the upstream agent dep that's extended by this project
val upstreamAgent: Configuration by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

val otelInstrumentationVersion: String by rootProject.extra

dependencies {
  add("upstreamAgent", platform(project(":dependencyManagement")))
  bootstrapLibs(project(":bootstrap"))

  javaagentLibs(project(":custom"))
  javaagentLibs(project(":instrumentation:servlet-3"))

  upstreamAgent("io.opentelemetry.javaagent:opentelemetry-javaagent")
}

fun CopySpec.isolateClasses(jars: Iterable<File>) {
  jars.forEach {
    from(zipTree(it)) {
      into("inst")
      rename("^(.*)\\.class\$", "\$1.classdata")
      // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
      rename("^LICENSE\$", "LICENSE.renamed")
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

  processResources {
    from(rootProject.file("licenses")) {
      into("META-INF/licenses")
    }
  }

  // building the final javaagent jar is done in 3 steps:

  // 1. all distro specific javaagent libs are relocated
  val relocateJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(javaagentLibs)

    duplicatesStrategy = DuplicatesStrategy.FAIL

    archiveFileName.set("javaagentLibs-relocated.jar")

    dependencies {
      // exclude known bootstrap dependencies - they can't appear in the inst/ directory
      exclude(dependency("org.slf4j:slf4j-api"))
      exclude(dependency("io.opentelemetry:opentelemetry-api"))
      exclude(dependency("io.opentelemetry:opentelemetry-context"))
      exclude(dependency("io.opentelemetry:opentelemetry-api-incubator"))
    }
  }

  // 2. the distro javaagent libs are then isolated - moved to the inst/ directory
  // having a separate task for isolating javaagent libs is required to avoid duplicates with the upstream javaagent
  // duplicatesStrategy in shadowJar won't be applied when adding files with with(CopySpec) because each CopySpec has
  // its own duplicatesStrategy
  val isolateJavaagentLibs by registering(Copy::class) {
    dependsOn(relocateJavaagentLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)

    into(layout.buildDirectory.dir("isolated/javaagentLibs"))
  }

  // 3. the relocated and isolated javaagent libs are merged together with the bootstrap libs (which undergo relocation
  // in this task) and the upstream javaagent jar; duplicates are removed
  shadowJar {
    configurations = listOf(bootstrapLibs, upstreamAgent)

    dependsOn(isolateJavaagentLibs)
    from(isolateJavaagentLibs.get().outputs)

    archiveClassifier.set("all")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    mergeServiceFiles {
      include("inst/META-INF/services/*")
    }
    exclude("**/module-info.class")

    manifest {
      attributes(
        mapOf(
          "Main-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
          "Agent-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
          "Premain-Class" to "io.opentelemetry.javaagent.OpenTelemetryAgent",
          "Can-Redefine-Classes" to true,
          "Can-Retransform-Classes" to true,
          "Implementation-Vendor" to "Demo",
          "Implementation-Version" to "demo-${project.version}-otel-$otelInstrumentationVersion",
        ),
      )
    }
  }

  assemble {
    dependsOn(shadowJar)
  }
} 
