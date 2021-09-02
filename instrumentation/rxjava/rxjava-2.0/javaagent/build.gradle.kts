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

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.rxjava.experimental-span-attributes=true")
}

dependencies {
  library("io.reactivex.rxjava2:rxjava:2.0.6")
  compileOnly(project(":instrumentation-api-annotation-support"))

  implementation(project(":instrumentation:rxjava:rxjava-2.0:library"))

  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation(project(":instrumentation:rxjava:rxjava-2.0:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
