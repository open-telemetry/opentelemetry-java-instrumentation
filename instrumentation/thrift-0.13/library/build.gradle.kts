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

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc")
  }

  val testBothSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=rpc/dup")
  }

  check {
    dependsOn(testStableSemconv, testBothSemconv)
  }
}
