plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.0.0,3.17.0)")
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  implementation(project(":instrumentation:redisson:redisson-common-3.0:javaagent"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:redisson:redisson-3.17:javaagent"))

  testImplementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.16.+") // see redisson-3.17 module
}

testing {
  suites {
    // Atomic batch execution was introduced in 3.7.2, but versions with atomic batch support before
    // 3.11.1 cannot run on Java 21 because URIBuilder reflects on Field.modifiers. 3.11.1 is the
    // earliest runnable version that exercises RedissonPromiseWrapper; the latest version covered
    // by this module uses BatchPromise and bypasses the wrapper.
    register<JvmTestSuite>("testRedisson3111") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
          include("**/RedissonAsyncClientTest.java")
        }
      }
      dependencies {
        implementation("org.redisson:redisson:3.11.1")
        implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      }
      targets.configureEach {
        testTask.configure {
          jvmArgs("-Dotel.semconv-stability.opt-in=database")
          systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  named<Test>("testRedisson3111") {
    systemProperty("testLatestDeps", true)
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }
}
