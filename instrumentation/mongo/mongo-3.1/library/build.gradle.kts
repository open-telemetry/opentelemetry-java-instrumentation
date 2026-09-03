plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.mongodb:mongo-java-driver:3.1.0")

  implementation(project(":instrumentation-api-incubator"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:mongo:mongo-3.1:testing"))
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

  val testConfiguredTargetBothSemconv = register<Test>("testConfiguredTargetBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching(
        "io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoConfiguredTargetTest"
      )
    }
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
  }

  check {
    dependsOn(testStableSemconv, testConfiguredTargetBothSemconv)
  }
}
