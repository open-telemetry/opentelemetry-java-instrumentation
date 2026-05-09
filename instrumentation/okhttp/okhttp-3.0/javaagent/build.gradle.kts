plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.squareup.okhttp3")
    module.set("okhttp")
    versions.set("[3.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  implementation(project(":instrumentation:okhttp:okhttp-3.0:library"))

  library("com.squareup.okhttp3:okhttp:3.0.0")

  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))

  testInstrumentation(project(":instrumentation:okhttp:okhttp-2.2:javaagent"))
}

testing {
  suites {
    val http2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("com.squareup.okhttp3:okhttp:${baseVersion("3.11.0").orLatest()}")
        if (otelProps.testLatestDeps) {
          compileOnly("com.google.android:annotations:4.1.1.4")
        }
        implementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
