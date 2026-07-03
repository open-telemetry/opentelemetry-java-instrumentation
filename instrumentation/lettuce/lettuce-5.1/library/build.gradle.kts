plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

otelJava {
  // OtelCommandArgsUtil needs package-private access to io.lettuce.core.protocol, which doesn't
  // work in OSGi where each bundle has its own class loader
  osgiEnabled.set(false)
}

dependencies {
  library("io.lettuce:lettuce-core:5.1.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation(project(":instrumentation:lettuce:lettuce-5.1:testing"))
  testImplementation(project(":instrumentation:reactor:reactor-3.1:library"))
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.lettuce.experimental.command-encoding-events.enabled=true")
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
