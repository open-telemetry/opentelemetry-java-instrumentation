plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:rediscala-1.8:javaagent"))
  testImplementation("com.github.etaty:rediscala_2.11:1.8.0")
}

val testStableSemconv = tasks.register<Test>("testStableSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=database")
}

tasks.check {
  dependsOn(testStableSemconv)
}
