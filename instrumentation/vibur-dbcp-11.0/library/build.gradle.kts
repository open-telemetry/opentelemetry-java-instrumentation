plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
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
