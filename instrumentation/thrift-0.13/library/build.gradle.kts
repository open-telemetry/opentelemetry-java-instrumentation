plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly(project(":muzzle")) // For @NoMuzzle
  compileOnly("org.apache.thrift:libthrift:0.21.0")
  testImplementation("org.apache.thrift:libthrift:0.13.0")
  testImplementation(project(":instrumentation:thrift-0.13:testing"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  val testPreviewSemconv = register<Test>("testPreviewSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.preview=rpc/dup")
  }

  check {
    dependsOn(testPreviewSemconv, testBothSemconv)
  }
}
