plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.lettuce")
    module.set("lettuce-core")
    // by default this module only applies to pre-5.1 (see classLoaderMatcher); under v3-preview it
    // also covers 5.1+, but muzzle cannot evaluate that runtime branch
    versions.set("[5.0.0.RELEASE,5.1.0.RELEASE)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.lettuce:lettuce-core:5.0.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation("com.google.guava:guava")
  testImplementation("io.lettuce:lettuce-core:5.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.1:javaagent"))

  latestDepTestLibrary("io.lettuce:lettuce-core:5.0.+") // see lettuce-5.1 module
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)

    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.experimental-span-attributes=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
  }

  // exercises the v3-preview path, where this advice-based module supersedes the SPI-based
  // lettuce-5.1 javaagent module (which is disabled under v3-preview)
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testStableSemconv, testExperimental, testV3Preview)
  }
}
