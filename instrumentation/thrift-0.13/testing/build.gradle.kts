plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")
  val thriftVersion = baseVersion("0.13.0").orLatest()
  implementation("org.apache.thrift:libthrift:$thriftVersion")
}

val generatedThriftDir = layout.buildDirectory.dir("generated/thrift/main")

val generateThriftSources by tasks.registering(Exec::class) {
  val thriftFilePath =
    layout.projectDirectory.file("src/main/thrift/custom.thrift").asFile.absolutePath
  val outputDirPath = generatedThriftDir.get().asFile.also { it.mkdirs() }.absolutePath
  inputs.file(thriftFilePath)
  outputs.dir(outputDirPath)

  standardOutput = System.out
  executable = "docker"
  // 0.22.0 is currently the latest available image
  val thriftVersion = if (otelProps.testLatestDeps) "0.22.0" else "0.13.0"
  args = listOf(
    "run",
    "--rm",
    "--platform=linux/amd64",
    "-v", "$thriftFilePath:/thrift/input/custom.thrift:ro",
    "-v", "$outputDirPath:/thrift/output",
    "ghcr.io/reddit/thrift-compiler:$thriftVersion",
    "--gen",
    "java",
    "-out",
    "/thrift/output",
    "/thrift/input/custom.thrift")
}

sourceSets {
  main {
    java {
      srcDir(generatedThriftDir)
    }
  }
}

tasks.compileJava {
  dependsOn(generateThriftSources)
}

tasks.sourcesJar {
  dependsOn(generateThriftSources)
}

tasks.named<Checkstyle>("checkstyleMain") {
  exclude("**/thrift/**")
}
