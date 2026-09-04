plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mongodb")
    module.set("mongo-java-driver")
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.mongodb")
    module.set("mongodb-driver-core")
    // this instrumentation is backwards compatible with early versions of the new API that shipped in 3.7
    // the legacy API instrumented in mongo-3.1 continues to be shipped in 4.x, but doesn't conflict here
    // because they are triggered by different types: MongoClientSettings(new) vs MongoClientOptions(legacy)
    versions.set("[3.7, 4.0)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:mongo:mongo-3.1:library"))

  // SocketStream.initializeSocket became protected in 3.11.
  library("org.mongodb:mongo-java-driver:3.11.0")
  latestDepTestLibrary("org.mongodb:mongo-java-driver:3.+") // see mongo-4.0 module

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))
  testImplementation(project(":instrumentation:mongo:mongo-common:testing"))
  testImplementation("com.github.jnr:jnr-unixsocket:0.18")

  testInstrumentation(project(":instrumentation:mongo:mongo-async-3.3:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:mongo:mongo-4.0:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
