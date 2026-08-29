plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:vertx:vertx-redis-client:vertx-redis-client-4.0:javaagent"))
  testImplementation("io.vertx:vertx-redis-client:4.0.0")
}

val testStableSemconv = tasks.register<Test>("testStableSemconv") {
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  jvmArgs("-Dotel.semconv-stability.opt-in=database")
}

tasks.check {
  dependsOn(testStableSemconv)
}
