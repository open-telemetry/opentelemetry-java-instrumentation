plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.9.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.thrift:libthrift:0.9.1")
  testLibrary("org.apache.thrift:libthrift:0.12.0")
  implementation(project(":instrumentation:thrift:thrift-common:javaagent"))
}

val generatedThriftDir = layout.buildDirectory.dir("generated/thrift/test")

val generateThriftSources by tasks.registering(Exec::class) {
  val thriftFilePath =
    layout.projectDirectory.file("src/test/resources/ThriftService.thrift").asFile.absolutePath
  val outputDirPath = generatedThriftDir.get().asFile.also { it.mkdirs() }.absolutePath
  inputs.file(thriftFilePath)
  outputs.dir(outputDirPath)

  standardOutput = System.out
  executable = "docker"
  args = listOf(
    "run",
    "--rm",
    "--platform=linux/amd64",
    "-v", "$thriftFilePath:/thrift/input/ThriftService.thrift:ro",
    "-v", "$outputDirPath:/thrift/output",
    "thrift:0.12.0",
    "thrift",
    "--gen",
    "java",
    "-out",
    "/thrift/output",
    "/thrift/input/ThriftService.thrift")
}

sourceSets {
  test {
    java {
      srcDir(generatedThriftDir)
    }
  }
}

tasks.compileTestJava {
  dependsOn(generateThriftSources)
}

tasks.named<Checkstyle>("checkstyleTest") {
  exclude("**/thrift/**")
}
