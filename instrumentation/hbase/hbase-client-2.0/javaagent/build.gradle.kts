plugins {
  id("otel.javaagent-instrumentation")
}

otelJava {
  // HBase 2.0.x test stack is not reliable on JDK 25+.
  maxJavaVersionForTests.set(JavaVersion.VERSION_24)
}

muzzle {
  pass {
    group.set("org.apache.hbase")
    module.set("hbase-client")
    versions.set("[2.0.0, 2.5.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:hbase:hbase-client-common:javaagent"))

  library("org.apache.hbase:hbase-client:2.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:hbase:hbase-client-common:testing"))
  testInstrumentation(project(":instrumentation:hbase:hbase-client-1.4:javaagent"))

  latestDepTestLibrary("org.apache.hbase:hbase-client:2.4.+") // native on-by-default instrumentation after this version
}

testing {
  suites {
    val shadedClientTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.apache.hbase:hbase-shaded-client:${baseVersion("2.0.0").orLatest("2.4.+")}")
        implementation(project(":instrumentation:hbase:hbase-client-common:testing"))
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

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
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
