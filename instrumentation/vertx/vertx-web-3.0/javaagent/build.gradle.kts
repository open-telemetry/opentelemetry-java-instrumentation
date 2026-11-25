import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-web")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("io.vertx:vertx-web:3.0.0")

  // We need both versions as different versions of Vert.x use different versions of Netty
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-http-client:vertx-http-client-4.0:javaagent"))
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

testing.suites {
  register<JvmTestSuite>("version3Test") {
    dependencies {
      implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

      val version = baseVersion("3.0.0").orLatest("3.+")
      implementation("io.vertx:vertx-web:$version")
      implementation("io.vertx:vertx-jdbc-client:$version")
      implementation("io.vertx:vertx-codegen:$version")
      implementation("io.vertx:vertx-docgen:$version")
    }
  }

  register<JvmTestSuite>("version41Test") {
    dependencies {
      implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

      val version = baseVersion("4.1.0").orLatest("4.+")
      implementation("io.vertx:vertx-web:$version")
      implementation("io.vertx:vertx-jdbc-client:$version")
      implementation("io.vertx:vertx-codegen:$version")
    }
  }

  register<JvmTestSuite>("version45Test") {
    sources {
      java.srcDir("src/version41Test/java")
    }
    dependencies {
      implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))
      implementation("io.vertx:vertx-web:4.5.3")
      implementation("io.vertx:vertx-codegen:4.5.3")
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      options.release.set(21)
    }
  }

  register<JvmTestSuite>("version5Test") {
    dependencies {
      implementation(project(":instrumentation:vertx:vertx-web-3.0:testing"))

      val version = baseVersion("5.0.0").orLatest()
      implementation("io.vertx:vertx-web:$version")
      implementation("io.vertx:vertx-jdbc-client:$version")
      implementation("io.vertx:vertx-codegen:$version")
    }
    project.tasks.named<JavaCompile>(sources.compileJavaTaskName) {
      options.release.set(11)
    }
  }

  executions("version3Test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }
  }

  executions("version45Test") {
    configureEach {
      filter {
        includeTestsMatching("*.multiHandlerRoute")
        includeTestsMatching("*.subRouterRouteIncludesMountPath")
      }
      if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_21)) {
        enabled = false
      }
    }
  }

  executions("version5Test") {
    configureEach {
      if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
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
