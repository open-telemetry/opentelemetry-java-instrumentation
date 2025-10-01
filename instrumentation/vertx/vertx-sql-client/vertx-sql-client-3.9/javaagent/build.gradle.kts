plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[3.9.0,4.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  val version = "3.9.16"
  library("io.vertx:vertx-sql-client:$version")
  library("io.vertx:vertx-codegen:$version")

  implementation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common:javaagent"))

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")
  testLibrary("io.vertx:vertx-junit5:$version")
  testLibrary("org.testcontainers:postgresql")

  latestDepTestLibrary("io.vertx:vertx-sql-client:3.9.+")
  latestDepTestLibrary("io.vertx:vertx-pg-client:3.9.+")
  latestDepTestLibrary("io.vertx:vertx-codegen:3.9.+")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
if (!latestDepTest) {
  otelJava {
    maxJavaVersionForTests.set(JavaVersion.VERSION_21)
  }
}
