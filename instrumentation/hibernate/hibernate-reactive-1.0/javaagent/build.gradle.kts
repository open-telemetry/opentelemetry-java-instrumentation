plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.hibernate.reactive")
    module.set("hibernate-reactive-core")
    versions.set("(,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("org.hibernate.reactive:hibernate-reactive-core:1.0.0.Final")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client-4.0:javaagent"))

  library("io.vertx:vertx-sql-client:4.4.2")
  compileOnly("io.vertx:vertx-codegen:4.4.2")

  testLibrary("io.vertx:vertx-pg-client:4.4.2")
  testLibrary("io.vertx:vertx-codegen:4.4.2")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val hibernateReactive1Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.testcontainers:testcontainers")
        if (latestDepTest) {
          implementation("org.hibernate.reactive:hibernate-reactive-core:1.+")
          implementation("io.vertx:vertx-pg-client:4.+")
        } else {
          implementation("org.hibernate.reactive:hibernate-reactive-core:1.0.0.Final")
          implementation("io.vertx:vertx-pg-client:4.1.5")
        }
        compileOnly("io.vertx:vertx-codegen:4.1.5")
      }
    }

    val hibernateReactive2Test by registering(JvmTestSuite::class) {
      dependencies {
        implementation("org.testcontainers:testcontainers")
        if (latestDepTest) {
          implementation("org.hibernate.reactive:hibernate-reactive-core:latest.release")
          implementation("io.vertx:vertx-pg-client:latest.release")
        } else {
          implementation("org.hibernate.reactive:hibernate-reactive-core:2.0.0.Final")
          implementation("io.vertx:vertx-pg-client:4.4.2")
        }
        compileOnly("io.vertx:vertx-codegen:4.4.2")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
  named("compileHibernateReactive2TestJava", JavaCompile::class).configure {
    options.release.set(11)
  }
  val testJavaVersion =
    gradle.startParameter.projectProperties.get("testJavaVersion")?.let(JavaVersion::toVersion)
      ?: JavaVersion.current()
  if (testJavaVersion.isJava8) {
    named("hibernateReactive2Test", Test::class).configure {
      enabled = false
    }
    if (latestDepTest) {
      named("hibernateReactive1Test", Test::class).configure {
        enabled = false
      }
    }
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites)
    dependsOn(testStableSemconv)
  }
}

if (!latestDepTest) {
  // https://bugs.openjdk.org/browse/JDK-8320431
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
