plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.elasticsearch.client:elasticsearch-rest-client:7.0.0")
  implementation("com.fasterxml.jackson.core:jackson-core")
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

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testStableSemconv, testV3Preview)
  }
}
