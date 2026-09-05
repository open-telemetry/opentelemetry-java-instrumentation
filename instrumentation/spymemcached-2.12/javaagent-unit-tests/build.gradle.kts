plugins {
  id("otel.java-conventions")
}

tasks {
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:spymemcached-2.12:javaagent"))
  testImplementation("net.spy:spymemcached:2.12.0")
}
