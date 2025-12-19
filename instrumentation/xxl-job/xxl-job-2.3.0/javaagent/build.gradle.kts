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
  library("org.apache.groovy:groovy")
  library("com.xuxueli:xxl-job-core:2.3.0") {
    exclude("org.codehaus.groovy", "groovy")
  }
  implementation(project(":instrumentation:xxl-job:xxl-job-common:javaagent"))

  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.1.2:javaagent"))
  testInstrumentation(project(":instrumentation:xxl-job:xxl-job-2.3.0:javaagent"))

  testImplementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))

  // latest version is tested in a separate test suite
  latestDepTestLibrary("com.xuxueli:xxl-job-core:3.2.+") // documented limitation
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val xxlJob33Test by registering(JvmTestSuite::class) {
      dependencies {
        val version = if (testLatestDeps) "latest.release" else "3.3.0"
        implementation("com.xuxueli:xxl-job-core:$version")
        implementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
    jvmArgs("-Dotel.instrumentation.xxl-job.experimental-span-attributes=true")
  }

  named("compileXxlJob33TestJava", JavaCompile::class).configure {
    options.release.set(17)
  }
  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named("xxlJob33Test", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
