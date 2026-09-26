plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":muzzle"))
}

tasks {
  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testV3Preview)
  }
}
