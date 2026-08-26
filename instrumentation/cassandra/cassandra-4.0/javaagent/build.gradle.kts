plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.0,4.4)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core-shaded")
    versions.set("[4.0,4.4)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.datastax.oss:java-driver-core:4.0.0")
  latestDepTestLibrary("com.datastax.oss:java-driver-core:4.3.+") // see cassandra-4.4 module

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:cassandra:cassandra-common-4.0:testing"))

  testInstrumentation(project(":instrumentation:cassandra:cassandra-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:cassandra:cassandra-4.4:javaagent"))
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

  fun registerShadedTest(name: String, version: String, stableSemconv: Boolean = false) {
    val shadedClasspath =
      configurations.create("${name}RuntimeClasspath") {
        isCanBeConsumed = false
        isCanBeResolved = true
        extendsFrom(configurations.testRuntimeClasspath.get())
        resolutionStrategy.dependencySubstitution {
          substitute(module("com.datastax.oss:java-driver-core"))
            .using(module("com.datastax.oss:java-driver-core-shaded:$version"))
        }
      }
    register<Test>(name) {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = files(sourceSets.test.get().output, shadedClasspath)
      if (stableSemconv) {
        jvmArgs("-Dotel.semconv-stability.opt-in=database")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
      }
    }
  }

  registerShadedTest("testShaded", "4.0.0")
  registerShadedTest("testShadedStableSemconv", "4.0.0", stableSemconv = true)
  registerShadedTest("testShadedLatest", "4.3.1")
}
