plugins {
  id("io.opentelemetry.instrumentation.javaagent-shadowing")

  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry Javaagent for testing"
group = "io.opentelemetry.javaagent"

val agent by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

val extensionLibs by configurations.creating {
  isCanBeResolved = true
  isCanBeConsumed = false
}

dependencies {
  extensionLibs(project(":testing:agent-exporter"))
  agent(project(":javaagent", "baseJar"))

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-api")
}

tasks {
  //Extracts manifest from OpenTelemetry Java agent to reuse it later
  val agentManifest by registering(Copy::class) {
    dependsOn(agent)
    from(zipTree(agent.singleFile).matching {
      include("META-INF/MANIFEST.MF")
    })
    into("$buildDir/tmp")
  }

  shadowJar.configure {
    //We use a configuration separate from "implementation" to make sure dependencies added to it
    //do not leak to test's classpath
    configurations = listOf(extensionLibs)
  }

  //Produces a copy of upstream javaagent with this extension jar included inside it
  //The location of extension directory inside agent jar is hard-coded in the agent source code
  val agentForTests by registering(Jar::class) {
    dependsOn(agentManifest)
//    archiveFileName.set("agent-for-testing.jar")
    manifest.from("$buildDir/tmp/META-INF/MANIFEST.MF")
    from(zipTree(agent.singleFile))
    from(shadowJar) {
      into("extensions")
    }
  }

  afterEvaluate {
    withType<Test>().configureEach {
      dependsOn(agentForTests)
      inputs.file(agentForTests.get().archiveFile)

      jvmArgs("-Dotel.javaagent.debug=true")
      jvmArgs("-javaagent:${agentForTests.get().archiveFile.get().asFile.absolutePath}")
    }
  }
}