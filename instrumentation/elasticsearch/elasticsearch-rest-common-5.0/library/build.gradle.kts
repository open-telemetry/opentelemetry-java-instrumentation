plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("org.elasticsearch.client:rest:5.0.0")
  compileOnly("com.google.auto.value:auto-value-annotations")

  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("org.elasticsearch.client:rest:5.0.0")
}

tasks {
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
