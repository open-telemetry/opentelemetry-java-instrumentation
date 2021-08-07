plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.kubernetes")
    module.set("client-java-api")
    versions.set("[7.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.kubernetes:client-java-api:7.0.0")

  implementation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))
}

tasks.withType<Test>().configureEach {
  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.kubernetes-client.experimental-span-attributes=true")
}
