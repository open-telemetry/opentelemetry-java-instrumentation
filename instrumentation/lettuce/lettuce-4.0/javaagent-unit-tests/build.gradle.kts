plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))
  testImplementation("biz.paluch.redis:lettuce:4.0.Final")
}

val testStableSemconv = tasks.register<Test>("testStableSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=database")
}

tasks.check {
  dependsOn(testStableSemconv)
}
