plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.elasticsearch.client")
    module.set("transport")
    versions.set("[6.0.0,)")
    // version 7.11.0 depends on org.elasticsearch:elasticsearch:7.11.0 which depends on
    // org.elasticsearch:elasticsearch-plugin-classloader:7.11.0 which does not exist
    // version 7.17.8 has broken module metadata
    skip("7.11.0", "7.17.8")
    // version 8.8.0 depends on elasticsearch:elasticsearch-preallocate which doesn't exist
    excludeDependency("org.elasticsearch:elasticsearch-preallocate")
    assertInverse.set(true)
  }
  pass {
    group.set("org.elasticsearch")
    module.set("elasticsearch")
    versions.set("[6.0.0,8.0.0)")
    // version 7.11.0 depends on org.elasticsearch:elasticsearch:7.11.0 which depends on
    // org.elasticsearch:elasticsearch-plugin-classloader:7.11.0 which does not exist
    skip("7.11.0")
    // version 8.8.0 depends on elasticsearch:elasticsearch-preallocate which doesn't exist
    excludeDependency("org.elasticsearch:elasticsearch-preallocate")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.elasticsearch.client:transport:6.0.0")

  implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:javaagent"))

  // Ensure no cross interference
  testInstrumentation(project(":instrumentation:elasticsearch:elasticsearch-rest-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:apache-httpasyncclient-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("org.elasticsearch.plugin:transport-netty4-client:6.0.0")

  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-transport-6.0:testing"))
  testImplementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val elasticsearch6Test by registering(JvmTestSuite::class) {
      dependencies {
        if (latestDepTest) {
          implementation("org.elasticsearch.client:transport:6.4.+")
          implementation("org.elasticsearch.plugin:transport-netty4-client:6.4.+")
        } else {
          implementation("org.elasticsearch.client:transport:6.0.0")
          implementation("org.elasticsearch.plugin:transport-netty4-client:6.0.0")
        }
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-6.0:testing"))
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
      }
    }

    val elasticsearch65Test by registering(JvmTestSuite::class) {
      dependencies {
        if (latestDepTest) {
          implementation("org.elasticsearch.client:transport:6.+")
          implementation("org.elasticsearch.plugin:transport-netty4-client:6.+")
        } else {
          implementation("org.elasticsearch.client:transport:6.5.0")
          implementation("org.elasticsearch.plugin:transport-netty4-client:6.5.0")
        }
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-6.0:testing"))
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
      }
    }

    val elasticsearch7Test by registering(JvmTestSuite::class) {
      dependencies {
        if (latestDepTest) {
          implementation("org.elasticsearch.client:transport:latest.release")
          implementation("org.elasticsearch.plugin:transport-netty4-client:latest.release")
        } else {
          implementation("org.elasticsearch.client:transport:7.0.0")
          implementation("org.elasticsearch.plugin:transport-netty4-client:7.0.0")
        }
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-6.0:testing"))
        implementation(project(":instrumentation:elasticsearch:elasticsearch-transport-common:testing"))
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)

    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("collectSpans", true)
  }

  val testSuites = testing.suites.withType(JvmTestSuite::class)

  val stableSemconvSuites = testSuites.map { suite ->
    register<Test>("${suite.name}StableSemconv") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.semconv-stability.opt-in=database")
      systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
    }
  }

  val experimentalSuites = testSuites.map { suite ->
    register<Test>("${suite.name}Experimental") {
      testClassesDirs = suite.sources.output.classesDirs
      classpath = suite.sources.runtimeClasspath

      jvmArgs("-Dotel.instrumentation.elasticsearch.experimental-span-attributes=true")
      systemProperty("metaDataConfig", "otel.instrumentation.elasticsearch.experimental-span-attributes=true")
    }
  }

  check {
    dependsOn(testing.suites, stableSemconvSuites, experimentalSuites)
  }
}
