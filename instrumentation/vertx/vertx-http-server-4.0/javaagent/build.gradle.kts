plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.vertx")
    module.set("vertx-core")
    versions.set("[4.0.0,5)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.vertx:vertx-core:4.0.0")
  library("io.vertx:vertx-codegen:4.0.0")

  // Vert.x 4.0's HTTP/1 decoder is incompatible with the current Netty test BOM.
  testLibrary("io.vertx:vertx-core:4.1.0")
  testLibrary("io.vertx:vertx-codegen:4.1.0")
  testLibrary("io.vertx:vertx-web:4.1.0")
  testImplementation("io.netty:netty-codec-haproxy")
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:vertx:vertx-web-3.0:javaagent"))

  latestDepTestLibrary("io.vertx:vertx-core:4.+")
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+")
  latestDepTestLibrary("io.vertx:vertx-web:4.+")
}

tasks.test {
  systemProperty(
    "test.keystore",
    rootProject.file("testing-common/src/misc/testing-keystore.p12").absolutePath
  )
  enabled = (otelProps.testJavaVersion ?: JavaVersion.current()).isCompatibleWith(JavaVersion.VERSION_11)
}

tasks.compileTestJava {
  options.release.set(11)
}
