plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.reactivex.rxjava2")
    module.set("rxjava")
    versions.set("[2.0.6,)")
    assertInverse.set(true)
  }
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

dependencies {
  library("io.reactivex.rxjava2:rxjava:2.0.6")
  compileOnly(project(":instrumentation-annotations-support"))

  implementation(project(":instrumentation:rxjava:rxjava-2.0:library"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:rxjava:rxjava-3.1.1:javaagent"))

  testImplementation(project(":instrumentation-annotations"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation(project(":instrumentation:rxjava:rxjava-2.0:testing"))
}
