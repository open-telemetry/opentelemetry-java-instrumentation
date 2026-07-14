plugins {
  id("otel.library-instrumentation")
  id("otel.instrumentation-version-class")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

instrumentationVersionClass {
  className.set("io.opentelemetry.instrumentation.okhttp.v3_0.internal.InstrumentationVersion")
}

dependencies {
  compileOnly(project(":muzzle"))
  compileOnly("com.squareup.okhttp3:okhttp:3.11.0")

  testLibrary("com.squareup.okhttp3:okhttp:3.0.0")
  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
}

testing {
  suites {
    register<JvmTestSuite>("http2Test") {
      dependencies {
        implementation(project())
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

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
