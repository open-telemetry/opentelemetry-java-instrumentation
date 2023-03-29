plugins {
  id("otel.library-instrumentation")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  testImplementation("io.github.netmikey.logunit:logunit-jul:1.1.3")
}
tasks.create("generateDocs", JavaExec::class) {
  group = "build"
  description = "Generate table for README.md"
  classpath = sourceSets.test.get().runtimeClasspath
  mainClass.set("io.opentelemetry.instrumentation.runtimetelemetryjfr.GenerateDocs")
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
    dependsOn(testG1)
    dependsOn(testPS)
    dependsOn(testSerial)
  }
}
