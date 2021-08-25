import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("io.opentelemetry.instrumentation.javaagent-shadowing")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
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

  // exclude dependencies that are to be placed in bootstrap - they won't be added to inst/
  exclude("org.slf4j")
  exclude("io.opentelemetry", "opentelemetry-api")
  exclude("io.opentelemetry", "opentelemetry-api-metrics")
  exclude("io.opentelemetry", "opentelemetry-semconv")
}

dependencies {
  bootstrapLibs(project(":instrumentation-api"))
  bootstrapLibs(project(":instrumentation-api-annotation-support"))
  bootstrapLibs(project(":javaagent-bootstrap"))
  bootstrapLibs(project(":javaagent-instrumentation-api"))
  bootstrapLibs("org.slf4j:slf4j-simple")

  javaagentLibs(project(":testing:agent-exporter"))
  javaagentLibs(project(":javaagent-extension-api"))
  javaagentLibs(project(":javaagent-tooling"))
  javaagentLibs(project(":muzzle"))

  // Include instrumentations instrumenting core JDK classes tp ensure interoperability with other instrumentation
  javaagentLibs(project(":instrumentation:executors:javaagent"))
  // FIXME: we should enable this, but currently this fails tests for google http client
  // testImplementation project(":instrumentation:http-url-connection:javaagent")
  javaagentLibs(project(":instrumentation:internal:internal-class-loader:javaagent"))
  javaagentLibs(project(":instrumentation:internal:internal-eclipse-osgi-3.6:javaagent"))
  javaagentLibs(project(":instrumentation:internal:internal-proxy:javaagent"))
  javaagentLibs(project(":instrumentation:internal:internal-reflection:javaagent"))
  javaagentLibs(project(":instrumentation:internal:internal-url-class-loader:javaagent"))

  // Many tests use OpenTelemetry API calls, e.g. via InstrumentationTestRunner.runWithSpan
  javaagentLibs(project(":instrumentation:opentelemetry-annotations-1.0:javaagent"))
  javaagentLibs(project(":instrumentation:opentelemetry-api-1.0:javaagent"))

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-api")
}

val javaagentDependencies = dependencies

// collect all bootstrap instrumentation dependencies
project(":instrumentation").subprojects {
  val subProj = this

  plugins.withId("otel.javaagent-bootstrap") {
    javaagentDependencies.run {
      add(bootstrapLibs.name, project(subProj.path))
    }
  }
}

tasks {
  val relocateJavaagentLibs by registering(ShadowJar::class) {
    configurations = listOf(javaagentLibs)

    archiveFileName.set("javaagentLibs-relocated.jar")

    // exclude bootstrap projects from javaagent libs - they won't be added to inst/
    dependencies {
      exclude(project(":instrumentation-api"))
      exclude(project(":instrumentation-api-annotation-support"))
      exclude(project(":javaagent-bootstrap"))
      exclude(project(":javaagent-instrumentation-api"))
    }
  }

  val shadowJar by existing(ShadowJar::class) {
    configurations = listOf(bootstrapLibs)

    dependsOn(relocateJavaagentLibs)
    isolateClasses(relocateJavaagentLibs.get().outputs.files)

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
      dependsOn(shadowJar)
      inputs.file(shadowJar.get().archiveFile)

      jvmArgs("-Dotel.javaagent.debug=true")
      jvmArgs("-javaagent:${shadowJar.get().archiveFile.get().asFile.absolutePath}")
    }
  }
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
