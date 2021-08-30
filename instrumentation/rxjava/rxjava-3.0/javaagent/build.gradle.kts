plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.reactivex.rxjava3")
    module.set("rxjava")
    versions.set("[3.0.0,3.1.0]")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.reactivex.rxjava3:rxjava:3.0.0")
  compileOnly(project(":instrumentation-api-annotation-support"))

  implementation(project(":instrumentation:rxjava:rxjava-3.0:library"))

  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation(project(":instrumentation:rxjava:rxjava-3.0:testing"))

  latestDepTestLibrary("io.reactivex.rxjava3:rxjava:3.1.0")
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.rxjava.experimental-span-attributes=true")

  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
