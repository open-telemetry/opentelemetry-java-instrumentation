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
  compileOnly(project(":instrumentation-api-annotation-support"))

  implementation(project(":instrumentation:rxjava:rxjava-3.1.1:library"))

  testImplementation(project(":instrumentation:rxjava:rxjava-3-common:testing"))

  testInstrumentation(project(":instrumentation:rxjava:rxjava-3.0:javaagent"))
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.rxjava.experimental-span-attributes=true")
}
