plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-redis-client")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-redis-client:4.0.0")
  compileOnly("io.vertx:vertx-codegen:4.0.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.4.5:javaagent"))

  testLibrary("io.vertx:vertx-codegen:4.0.0")
}

testing {
  suites {
    register<JvmTestSuite>("test403") {
      dependencies {
        implementation("io.vertx:vertx-redis-client:4.0.3")
        implementation("io.vertx:vertx-codegen:4.0.3")
        implementation("org.testcontainers:testcontainers")
      }
    }
    register<JvmTestSuite>("test445") {
      dependencies {
        implementation("io.vertx:vertx-redis-client:4.4.5")
        implementation("io.vertx:vertx-codegen:4.4.5")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
  }

  val test403StableSemconv = register<Test>("test403StableSemconv") {
    val test403 = sourceSets.named("test403")
    testClassesDirs = files(test403.map { it.output.classesDirs })
    classpath = files(test403.map { it.runtimeClasspath })
    jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
  }

  val test445StableSemconv = register<Test>("test445StableSemconv") {
    val test445 = sourceSets.named("test445")
    testClassesDirs = files(test445.map { it.output.classesDirs })
    classpath = files(test445.map { it.runtimeClasspath })
    jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
  }

  check {
    dependsOn(testStableSemconv)
    if (!otelProps.testLatestDeps) {
      dependsOn(
        testing.suites.named("test403"),
        test403StableSemconv,
        testing.suites.named("test445"),
        test445StableSemconv,
      )
    }
  }
}
