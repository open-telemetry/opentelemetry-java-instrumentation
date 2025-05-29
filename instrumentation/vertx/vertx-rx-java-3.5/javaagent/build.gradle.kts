plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-rx-java2")
    versions.set("[3.5.0,)")
  }
}

// The first Vert.x version that uses rx-java 2
val vertxVersion = "3.5.0"

dependencies {
  compileOnly("io.vertx:vertx-web:$vertxVersion")
  compileOnly("io.vertx:vertx-rx-java2:$vertxVersion")

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val version35Test by registering(JvmTestSuite::class) {
      dependencies {
        // this only exists to make Intellij happy since it doesn't (currently at least) understand our
        // inclusion of this artifact inside :testing-common
        compileOnly(project.dependencies.project(":testing:armeria-shaded-for-testing", configuration = "shadow"))

        val version = if (testLatestDeps) "3.+" else "3.5.0"
        implementation("org.hsqldb:hsqldb:2.3.4")
        compileOnly("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-rx-java2:$version")
        implementation("io.vertx:vertx-web-client:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-circuit-breaker:$version")
      }
    }

    val version41Test by registering(JvmTestSuite::class) {
      dependencies {
        // this only exists to make Intellij happy since it doesn't (currently at least) understand our
        // inclusion of this artifact inside :testing-common
        compileOnly(project.dependencies.project(":testing:armeria-shaded-for-testing", configuration = "shadow"))

        val version = if (testLatestDeps) "4.+" else "4.1.0"
        implementation("org.hsqldb:hsqldb:2.3.4")
        compileOnly("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-rx-java2:$version")
        implementation("io.vertx:vertx-web-client:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-circuit-breaker:$version")
      }
    }

    val version5Test by registering(JvmTestSuite::class) {
      dependencies {
        // this only exists to make Intellij happy since it doesn't (currently at least) understand our
        // inclusion of this artifact inside :testing-common
        compileOnly(project.dependencies.project(":testing:armeria-shaded-for-testing", configuration = "shadow"))

        val version = if (testLatestDeps) "latest.release" else "5.0.0"
        implementation("org.hsqldb:hsqldb:2.3.4")
        compileOnly("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.vertx:vertx-rx-java2:$version")
        implementation("io.vertx:vertx-web-client:$version")
        implementation("io.vertx:vertx-jdbc-client:$version")
        implementation("io.vertx:vertx-circuit-breaker:$version")
      }
    }
  }
}

tasks {
  named("compileVersion5TestJava", JavaCompile::class).configure {
    options.release.set(11)
  }
  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
    named("version5Test", Test::class).configure {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
