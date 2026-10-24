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

  latestDepTestLibrary("io.vertx:vertx-core:4.+") // related dependency
  latestDepTestLibrary("io.vertx:vertx-codegen:4.+") // related dependency
  latestDepTestLibrary("io.vertx:vertx-web:4.+") // related dependency
}

testing {
  suites {
    register<JvmTestSuite>("version40Test") {
      sources.java.setSrcDirs(listOf("src/test/java"))
      dependencies {
        implementation("io.vertx:vertx-core:4.0.0")
        implementation("io.vertx:vertx-codegen:4.0.0")
        implementation("io.vertx:vertx-web:4.0.0")
        implementation("io.netty:netty-codec-haproxy")
      }
    }
    register<JvmTestSuite>("version43Test") {
      sources.java.setSrcDirs(listOf("src/test/java"))
      dependencies {
        val version = baseVersion("4.3.7").orLatest("4.+")
        implementation("io.vertx:vertx-core:$version")
        implementation("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.netty:netty-codec-haproxy")
      }
    }
    register<JvmTestSuite>("version45Test") {
      sources.java.setSrcDirs(listOf("src/test/java"))
      dependencies {
        val version = baseVersion("4.5.35").orLatest("4.+")
        implementation("io.vertx:vertx-core:$version")
        implementation("io.vertx:vertx-codegen:$version")
        implementation("io.vertx:vertx-web:$version")
        implementation("io.netty:netty-codec-haproxy")
      }
    }
  }
}

tasks.named<Test>("version40Test") {
  useJUnitPlatform {
    excludeTags("http1")
  }
}

tasks.named<Test>("version45Test") {
  systemProperty("test.vertx.http2.syncTrailerWrite", "true")
}

tasks.withType<Test>().configureEach {
  systemProperty(
    "test.keystore",
    rootProject.file("testing-common/src/misc/testing-keystore.p12").absolutePath
  )
  enabled = (otelProps.testJavaVersion ?: JavaVersion.current()).isCompatibleWith(JavaVersion.VERSION_11)
}

tasks.compileTestJava {
  options.release.set(11)
}

tasks.named<JavaCompile>("compileVersion43TestJava") {
  options.release.set(11)
}

tasks.named<JavaCompile>("compileVersion40TestJava") {
  options.release.set(11)
}

tasks.named<JavaCompile>("compileVersion45TestJava") {
  options.release.set(11)
}

tasks.check {
  dependsOn(testing.suites)
}
