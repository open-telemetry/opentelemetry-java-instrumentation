plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-sql-client")
    versions.set("[5.0.0,)")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

sourceSets {
  test {
    java.srcDir(
      if (otelProps.testLatestDeps) {
        "src/testVersion5_1/java"
      } else {
        "src/testVersion5_0/java"
      },
    )
  }
}

dependencies {
  val version = "5.0.0"
  library("io.vertx:vertx-sql-client:$version")
  library("io.vertx:vertx-codegen:$version")

  compileOnly(project(":muzzle")) // For @NoMuzzle

  implementation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-common-4.0:javaagent"))

  testInstrumentation(project(":instrumentation:jdbc:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-sql-client:vertx-sql-client-4.0:javaagent"))

  testLibrary("io.vertx:vertx-pg-client:$version")
  testLibrary("io.vertx:vertx-oracle-client:$version")
  testLibrary("io.vertx:vertx-jdbc-client:$version")
  testImplementation("io.agroal:agroal-pool:2.5")
  testImplementation("org.hsqldb:hsqldb:2.3.4")

  latestDepTestLibrary("io.vertx:vertx-sql-client:latest.release")
  latestDepTestLibrary("io.vertx:vertx-codegen:latest.release")
  latestDepTestLibrary("io.vertx:vertx-pg-client:latest.release")
  latestDepTestLibrary("io.vertx:vertx-oracle-client:latest.release")
  latestDepTestLibrary("io.vertx:vertx-jdbc-client:latest.release")
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

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database/dup,service.peer")
    filter {
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientTest.testConnectingToSupplierCapturesTheSuppliedOptions",
      )
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientTest.testConnectingToServerListReportsTheWholeConfiguredTarget",
      )
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientTest.testConnectingToServerListWithUnixSocketOmitsStableTarget",
      )
      includeTestsMatching(
        "io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.v5_0.VertxSqlClientTest.testOracleSupplierConnectFailureCapturesSuppliedOptions",
      )
    }
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
