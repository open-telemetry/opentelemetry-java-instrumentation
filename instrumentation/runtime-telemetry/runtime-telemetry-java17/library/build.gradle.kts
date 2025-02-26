plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:runtime-telemetry:runtime-telemetry-java8:library"))
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
}

tasks.create("generateDocs", JavaExec::class) {
  group = "build"
  description = "Generate table for README.md"
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("io.opentelemetry.instrumentation.runtimemetrics.java17.GenerateDocs")
  systemProperties.set("jfr.readme.path", project.projectDir.toString() + "/README.md")
}

tasks {
  val testG1 by registering(Test::class) {
    filter {
      includeTestsMatching("*G1GcMemoryMetricTest*")
    }
    include("**/*G1GcMemoryMetricTest.*")
    jvmArgs("-XX:+UseG1GC")
  }

  val testPS by registering(Test::class) {
    filter {
      includeTestsMatching("*PsGcMemoryMetricTest*")
    }
    include("**/*PsGcMemoryMetricTest.*")
    jvmArgs("-XX:+UseParallelGC")
  }

  val testSerial by registering(Test::class) {
    filter {
      includeTestsMatching("*SerialGcMemoryMetricTest*")
    }
    include("**/*SerialGcMemoryMetricTest.*")
    jvmArgs("-XX:+UseSerialGC")
  }

  test {
    filter {
      excludeTestsMatching("*G1GcMemoryMetricTest")
      excludeTestsMatching("*SerialGcMemoryMetricTest")
      excludeTestsMatching("*PsGcMemoryMetricTest")
    }
  }

  check {
    dependsOn(testG1)
    dependsOn(testPS)
    dependsOn(testSerial)
  }

  tasks {
    compileJava {
      // We compile this module for java 8 because it is used as a dependency in spring-boot-autoconfigure.
      // If this module is compiled for java 17 then gradle can figure out based on the metadata that
      // spring-boot-autoconfigure has a dependency that requires 17 and fails the build when it is used
      // in a project that targets an earlier java version.
      // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/13384
      sourceCompatibility = "1.8"
      targetCompatibility = "1.8"
      options.release.set(null as Int?)
    }
  }
}
