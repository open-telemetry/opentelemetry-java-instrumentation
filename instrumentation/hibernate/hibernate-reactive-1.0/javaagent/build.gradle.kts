import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
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
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-5.0:javaagent"))

  library("io.vertx:vertx-sql-client:4.4.2")
  compileOnly("io.vertx:vertx-codegen:4.4.2")

  testLibrary("io.vertx:vertx-pg-client:4.4.2")
  testLibrary("io.vertx:vertx-codegen:4.4.2")
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

testing.suites {
  register<JvmTestSuite>("hibernateReactive1Test") {
    dependencies {
      implementation("org.testcontainers:testcontainers")
      implementation("org.hibernate.reactive:hibernate-reactive-core:${baseVersion("1.0.0.Final").orLatest("1.+")}")
      implementation("io.vertx:vertx-pg-client:${baseVersion("4.1.5").orLatest("4.+")}")
      compileOnly("io.vertx:vertx-codegen:4.1.5")
    }
  }

  register<JvmTestSuite>("hibernateReactive2Test") {
    dependencies {
      implementation("org.testcontainers:testcontainers")
      implementation(project(":instrumentation:hibernate:hibernate-reactive-1.0:hibernate-reactive-2.0-testing"))
      implementation("org.hibernate.reactive:hibernate-reactive-core:${baseVersion("2.0.0.Final").orLatest("3.+")}")
      implementation("io.vertx:vertx-pg-client:${baseVersion("4.4.2").orLatest("4.+")}")
      compileOnly("io.vertx:vertx-codegen:4.4.2")
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      options.release.set(11)
    }
  }

  register<JvmTestSuite>("hibernateReactive4Test") {
    dependencies {
      implementation("org.testcontainers:testcontainers")
      implementation(project(":instrumentation:hibernate:hibernate-reactive-1.0:hibernate-reactive-2.0-testing"))
      implementation("org.hibernate.reactive:hibernate-reactive-core:${baseVersion("4.0.0.Final").orLatest()}")
      implementation("io.vertx:vertx-pg-client:${baseVersion("5.0.0").orLatest()}")
      compileOnly("io.vertx:vertx-codegen:4.4.2")
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      options.release.set(17)
    }
  }

  executions("hibernateReactive1Test") {
    configureEach {
      if (testJavaVersion.isJava8 && otelProps.testLatestDeps) {
        enabled = false
      }
    }
  }

  executions("hibernateReactive2Test") {
    configureEach {
      if (testJavaVersion.isJava8) {
        enabled = false
      }
    }
  }

  executions("hibernateReactive4Test") {
    configureEach {
      if (testJavaVersion.isJava8 || testJavaVersion.isJava11) {
        enabled = false
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

if (!otelProps.testLatestDeps) {
  // https://bugs.openjdk.org/browse/JDK-8320431
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
