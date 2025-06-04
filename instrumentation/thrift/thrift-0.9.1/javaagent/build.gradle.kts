plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.thrift")
    module.set("libthrift")
    versions.set("[0.9.1,)")
  }
}
val thriftExecutable = "./src/test/resources/thrift"
val thriftInputFile = "$projectDir/src/test/resources/ThriftService.thrift"
val thriftOutputDir = "$projectDir/src/test/java"

var generateThrift = tasks.register<Exec>("generateThrift") {
  group = "build"
  description = "Generate Java code from Thrift IDL files"
  commandLine(thriftExecutable, "--gen", "java", "-out", thriftOutputDir, thriftInputFile)
}

tasks.named<JavaCompile>("compileTestJava") {
  dependsOn(generateThrift)

  doFirst {
    source.forEach { file ->
      if (file.absolutePath.contains("$thriftOutputDir/io/opentelemetry/javaagent/instrumentation/thrift/v0_9_1/thrift/ThriftService.java")) {
        options.compilerArgs.add("-nowarn")
        options.compilerArgs.add("-Xlint:-unchecked")
      }
    }
  }
}

tasks.named<Checkstyle>("checkstyleTest") {
  exclude("**/thrift/ThriftService.java")
}

spotless {
  java {
    targetExclude("**/thrift/ThriftService.java")
  }
}

dependencies {
  compileOnly("org.apache.thrift:libthrift:0.9.1")
  implementation(project(":instrumentation:thrift:thrift-common:library"))

  testImplementation("org.apache.thrift:libthrift:0.9.1")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}
