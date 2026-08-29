plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:lettuce:lettuce-5.0:javaagent"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.lettuce:lettuce-core:5.0.0.RELEASE")
}

val testStableSemconv = tasks.register<Test>("testStableSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=database")
}

tasks.check {
  dependsOn(testStableSemconv)
}
