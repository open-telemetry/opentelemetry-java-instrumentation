plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.dubbo")
    module.set("dubbo")
    versions.set("[2.7,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:apache-dubbo-2.7:library-autoconfigure"))

  library("org.apache.dubbo:dubbo:2.7.0")
}

testing {
  suites {
    // using a test suite to ensure that project(":instrumentation:apache-dubbo-2.7:library-autoconfigure")
    // is not available on test runtime class path, otherwise instrumentation from library-autoconfigure
    // module would be used instead of the javaagent instrumentation that we want to test
    register<JvmTestSuite>("testDubbo") {
      dependencies {
        implementation(project(":instrumentation:apache-dubbo-2.7:testing"))
        val version = baseVersion("2.7.0").orLatest()
        implementation("org.apache.dubbo:dubbo:$version")
        implementation("org.apache.dubbo:dubbo-config-api:$version")
        if (otelProps.testLatestDeps) {
          // these dependencies should be optional but when running on jdk25 more classes are loaded than
          // on earlier jdk versions and DubboAgentRegistryTest fails with NoClassDefFoundError
          implementation("com.alibaba:fastjson:2.0.65")
          implementation("com.google.code.gson:gson:2.14.0")
          implementation("com.fasterxml.jackson.core:jackson-databind")
          implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
        }
      }
    }
    if (!otelProps.testLatestDeps) {
      // The shared testing project brings in Dubbo 2.7.5, which wins over testDubbo's direct 2.7.0
      // request. Exclude it here to exercise the earlier completion APIs without loading tests that
      // depend on DubboBootstrap.
      register<JvmTestSuite>("testDubbo270") {
        sources {
          java {
            setSrcDirs(listOf("src/testDubbo/java"))
            include("**/DubboAgentAsyncTest.java")
          }
        }
        dependencies {
          implementation(project(":instrumentation:apache-dubbo-2.7:testing")) {
            exclude(group = "org.apache.dubbo")
          }
          val version = baseVersion("2.7.0").orLatest()
          implementation("org.apache.dubbo:dubbo:$version")
          implementation("org.apache.dubbo:dubbo-config-api:$version")
        }
      }
      register<JvmTestSuite>("testDubbo272") {
        sources {
          java {
            setSrcDirs(listOf("src/testDubbo/java"))
            include("**/DubboAgentAsyncTest.java")
          }
        }
        dependencies {
          implementation(project(":instrumentation:apache-dubbo-2.7:testing")) {
            exclude(group = "org.apache.dubbo")
          }
          val version = baseVersion("2.7.2").orLatest()
          implementation("org.apache.dubbo:dubbo:$version")
          implementation("org.apache.dubbo:dubbo-config-api:$version")
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}

val previewSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}PreviewSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }
}

val bothSemconvSuites = testing.suites.withType(JvmTestSuite::class).map { suite ->
  tasks.register<Test>("${suite.name}BothSemconv") {
    testClassesDirs = suite.sources.output.classesDirs
    classpath = suite.sources.runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc/dup,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
  }
}

tasks {
  check {
    dependsOn(testing.suites, previewSemconvSuites, bothSemconvSuites)
  }
}
