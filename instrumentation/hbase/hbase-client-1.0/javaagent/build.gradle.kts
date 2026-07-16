plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // HBase 1.0.x does not support Java 9+.
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
}

muzzle {
  pass {
    group.set("org.apache.hbase")
    module.set("hbase-client")
    versions.set("[1.0.0,1.4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:hbase:hbase-client-common-1.0:javaagent"))

  library("org.apache.hbase:hbase-client:1.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  compileOnly("org.apache.hbase:hbase-shaded-client:1.1.0")

  testImplementation(project(":instrumentation:hbase:hbase-client-common-1.0:testing"))
  testInstrumentation(project(":instrumentation:hbase:hbase-client-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:hbase:hbase-client-2.0:javaagent"))

  latestDepTestLibrary("org.apache.hbase:hbase-client:1.+") // see hbase-client-1.4 module
}

configurations
  .matching { it.name == "testRuntimeClasspath" || it.name == "shadedClientTestRuntimeClasspath" }
  .configureEach {
    resolutionStrategy.force("com.google.guava:guava:12.0.1")
  }

testing {
  suites {
    register<JvmTestSuite>("shadedClientTest") {
      dependencies {
        implementation("org.apache.hbase:hbase-shaded-client:${baseVersion("1.2.6").orLatest("1.+")}")
        implementation(project(":instrumentation:hbase:hbase-client-common-1.0:testing"))
      }
    }
  }
}

abstract class HbaseBuildService : BuildService<BuildServiceParameters.None>

// HBase test container binds fixed host ports, disallow running tests in parallel.
gradle.sharedServices.registerIfAbsent("hbaseBuildService", HbaseBuildService::class.java) {
  maxParallelUsages.convention(1)
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    usesService(gradle.sharedServices.registrations["hbaseBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val stableSemconvSuites =
    testing.suites.withType(JvmTestSuite::class).map { suite ->
      register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.opt-in=database")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
      }
    }

  check {
    dependsOn(testing.suites, stableSemconvSuites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
