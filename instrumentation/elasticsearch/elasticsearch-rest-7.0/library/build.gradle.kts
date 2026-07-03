plugins {
  id("otel.library-instrumentation")
}

otelJava {
  // RestClientPackageAccess needs package-private access to org.elasticsearch.client, which
  // doesn't work in OSGi where each bundle has its own class loader
  osgiEnabled.set(false)
}

dependencies {
  library("org.elasticsearch.client:elasticsearch-rest-client:7.0.0")
  implementation("net.bytebuddy:byte-buddy")
  implementation(project(":instrumentation:elasticsearch:elasticsearch-rest-common-5.0:library"))

  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("org.testcontainers:testcontainers-elasticsearch")
}

tasks {
  withType<Test>().configureEach {
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
