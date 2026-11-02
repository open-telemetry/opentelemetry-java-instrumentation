import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[2.3.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  // groovy does not support 25-ea
  maxJavaVersionForTests.set(JavaVersion.VERSION_24)
}

dependencies {
  library("com.xuxueli:xxl-job-core:2.3.0") {
    exclude("org.codehaus.groovy", "groovy")
  }
  implementation(project(":instrumentation:xxl-job:xxl-job-common-1.9.2:javaagent"))

  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-1.9.2:javaagent"))
  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.1.2:javaagent"))
  testImplementation("org.apache.groovy:groovy")
  testImplementation(project(":instrumentation:xxl-job:xxl-job-common-1.9.2:testing"))

  // latest version is tested in a separate test suite
  latestDepTestLibrary("com.xuxueli:xxl-job-core:3.2.+") // see test suite below
}

testing {
  suites {
    register<JvmTestSuite>("xxlJob33Test") {
      dependencies {
        val version = baseVersion("3.3.0").orLatest()
        implementation("com.xuxueli:xxl-job-core:$version")
        implementation(project(":instrumentation:xxl-job:xxl-job-common-1.9.2:testing"))
      }
    }
  }
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

testing.suites.executions("test") {
  register("Experimental") {
    telemetryConfig("otel.instrumentation.xxl-job.experimental-span-attributes", "true")
  }
}

testing.suites.executions("xxlJob33Test") {
  configureEach {
    if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
      enabled = false
    }
  }

  register("Experimental") {
    telemetryConfig("otel.instrumentation.xxl-job.experimental-span-attributes", "true")
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  }

  named("compileXxlJob33TestJava", JavaCompile::class).configure {
    options.release.set(17)
  }

  check {
    dependsOn(testing.suites)
  }
}
