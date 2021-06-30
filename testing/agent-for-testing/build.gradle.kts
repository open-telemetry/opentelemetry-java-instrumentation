import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.shadow-conventions")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
group = "io.opentelemetry.javaagent"

fun isolateSpec(shadowJarTasks: Collection<Jar>): CopySpec = copySpec {
  from(shadowJarTasks.map { zipTree(it.archiveFile) }) {
    // important to keep prefix "inst" short, as it is prefixed to lots of strings in runtime mem
    into("inst")
    rename("""(^.*)\.class$""", "$1.classdata")
    // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
    rename("""^LICENSE$""", "LICENSE.renamed")
  }
}

val shadowInclude by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

evaluationDependsOn(":testing:agent-exporter")

tasks {
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

  val shadowJar by existing(ShadowJar::class) {
    configurations = listOf(shadowInclude)

    archiveClassifier.set("")

    dependsOn(":testing:agent-exporter:shadowJar")
    with(isolateSpec(listOf(project(":testing:agent-exporter").tasks.getByName<ShadowJar>("shadowJar"))))

    manifest.inheritFrom(jar.get().manifest)
  }

  afterEvaluate {
    withType<Test>().configureEach {
      inputs.file(shadowJar.get().archiveFile)

      jvmArgs("-Dotel.javaagent.debug=true")
      jvmArgs("-javaagent:${shadowJar.get().archiveFile.get().asFile.absolutePath}")

      dependsOn(shadowJar)
    }
  }
}

dependencies {
  // Dependencies to include without obfuscation.
  shadowInclude(project(":javaagent-bootstrap"))

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-api")
}

// Because shadow does not use default configurations
publishing {
  publications {
    named<MavenPublication>("maven") {
      project.shadow.component(this)
    }
  }
}
