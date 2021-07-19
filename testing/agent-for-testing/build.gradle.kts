import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("io.opentelemetry.instrumentation.javaagent-shadowing")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
group = "io.opentelemetry.javaagent"

val bootstrapLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}
val javaagentLibs by configurations.creating

dependencies {
  bootstrapLibs(project(":instrumentation-api"))
  bootstrapLibs(project(":instrumentation-api-annotation-support"))
  bootstrapLibs(project(":javaagent-bootstrap"))
  bootstrapLibs(project(":javaagent-instrumentation-api"))
  bootstrapLibs("org.slf4j:slf4j-simple")

  javaagentLibs(project(":testing:agent-exporter", configuration = "shadow"))

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-api")
}

val javaagentDependencies = dependencies

// collect all bootstrap instrumentation dependencies
project(":instrumentation").subprojects {
  val subProj = this

  plugins.withId("java") {
    if (subProj.name == "bootstrap") {
      javaagentDependencies.run {
        add(bootstrapLibs.name, project(subProj.path))
      }
    }
  }
}

fun isolateAgentClasses (jars: Iterable<File>): CopySpec {
  return copySpec {
    jars.forEach {
      from(zipTree(it)) {
        // important to keep prefix "inst" short, as it is prefixed to lots of strings in runtime mem
        into("inst")
        rename("""(^.*)\.class$""", "$1.classdata")
        // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
        rename("""^LICENSE$""", "LICENSE.renamed")
      }
    }
  }
}

evaluationDependsOn(":testing:agent-exporter")

tasks {
  jar {
    enabled = false
  }

  val shadowJar by existing(ShadowJar::class) {
    dependsOn(":testing:agent-exporter:shadowJar")

    configurations = listOf(bootstrapLibs)

    with(isolateAgentClasses(javaagentLibs.files))

    archiveClassifier.set("")

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

  afterEvaluate {
    withType<Test>().configureEach {
      inputs.file(shadowJar.get().archiveFile)

      jvmArgs("-Dotel.javaagent.debug=true")
      jvmArgs("-javaagent:${shadowJar.get().archiveFile.get().asFile.absolutePath}")

      dependsOn(shadowJar)
    }
  }

  // Because shadow does not use default configurations
  publishing {
    publications {
      named<MavenPublication>("maven") {
        project.shadow.component(this)
      }
    }
  }
}
