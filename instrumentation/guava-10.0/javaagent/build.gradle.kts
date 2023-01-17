plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.google.guava")
    module.set("guava")
    versions.set("[10.0,]")
    assertInverse.set(true)
  }
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.guava.experimental-span-attributes=true")
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.google.guava:guava:10.0")
  compileOnly(project(":instrumentation-annotations-support"))

  implementation(project(":instrumentation:guava-10.0:library"))

  testInstrumentation(project(":instrumentation:opentelemetry-extension-annotations-1.0:javaagent"))

  testImplementation(project(":instrumentation-annotations-support-testing"))
  testImplementation(project(":instrumentation-annotations"))
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
}
