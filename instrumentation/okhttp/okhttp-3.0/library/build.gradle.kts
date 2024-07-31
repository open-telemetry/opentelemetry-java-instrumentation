plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

dependencies {
  library("com.squareup.okhttp3:okhttp:3.0.0")

  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val http2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project())
        if (testLatestDeps) {
          implementation("com.squareup.okhttp3:okhttp:+")
          compileOnly("com.google.android:annotations:4.1.1.4")
        } else {
          implementation("com.squareup.okhttp3:okhttp:3.11.0")
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
}
