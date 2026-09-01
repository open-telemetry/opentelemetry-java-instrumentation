plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("biz.paluch.redis:lettuce:4.0.Final")
  testImplementation("io.netty:netty-transport-native-epoll:4.0.56.Final:linux-x86_64")
}

tasks {
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database/dup")
  }

  check {
    dependsOn(testBothSemconv, testStableSemconv)
  }
}
