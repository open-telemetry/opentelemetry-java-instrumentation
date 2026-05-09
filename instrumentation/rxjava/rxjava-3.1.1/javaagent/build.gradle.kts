plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.reactivex.rxjava3")
    module.set("rxjava")
    versions.set("[3.1.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.1.1")
  compileOnly(project(":instrumentation-annotations-support"))

  implementation(project(":instrumentation:rxjava:rxjava-3.1.1:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-common-3.0:testing"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-3.0:javaagent"))
}

tasks {
  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.rxjava.experimental-span-attributes=true")
  }

  check {
    dependsOn(testExperimental)
  }
}
