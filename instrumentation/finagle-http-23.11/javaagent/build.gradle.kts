plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.twitter")
    module.set("finagle-http_2.12")
    versions.set("[23.11.0,]")
  }

  pass {
    group.set("com.twitter")
    module.set("finagle-http_2.13")
    versions.set("[23.11.0,]")
  }
}

val finagleVersion = "23.11.0"
val scalaVersion = "2.13.10"

val scalaMinor = Regex("""^([0-9]+\.[0-9]+)\.?.*$""").find(scalaVersion)!!.run {
  val (minorVersion) = this.destructured
  minorVersion
}

val scalified = fun(pack: String): String = "${pack}_$scalaMinor"

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("${scalified("com.twitter:finagle-http")}:$finagleVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  // Exclude the Promise stub and its nested classes;
  // this allows us to compile against these types in the instrumentation
  // despite them being private in their original inner class;
  // this is required for VirtualField to have a concrete type to find/get/set on.
  compileOnly(project(":instrumentation:finagle-http-23.11:compile-stub"))

  implementation(project(":instrumentation:netty:netty-4.1:javaagent"))
  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:netty:netty-common-4.0:javaagent"))
  implementation(project(":instrumentation:netty:netty-common-4.0:library"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    jvmArgs("-Dotel.instrumentation.http.client.emit-experimental-telemetry=true")
    jvmArgs("-Dotel.instrumentation.http.server.emit-experimental-telemetry=true")
    jvmArgs("-Dio.opentelemetry.context.enableStrictContext=true")

    // force the netty event loop into constrained territory
    systemProperty("io.netty.eventLoopThreads", "2")
    // ensure concurrent tests are competing for netty workers
    systemProperty("com.twitter.finagle.netty4.numWorkers", "2")
    // ensure concurrent tests are competing for offload pool workers
    systemProperty("com.twitter.finagle.offload.numWorkers", "2")
  }

  test {
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.http.client.emit-experimental-telemetry=true," +
        "otel.instrumentation.http.server.emit-experimental-telemetry=true"
    )
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")

    systemProperty(
      "metadataConfig",
      "otel.instrumentation.http.client.emit-experimental-telemetry=true," +
        "otel.instrumentation.http.server.emit-experimental-telemetry=true," +
        "otel.semconv-stability.opt-in=service.peer"
    )
  }

  check {
    dependsOn(testStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
