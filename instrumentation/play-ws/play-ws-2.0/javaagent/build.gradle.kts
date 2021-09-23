plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {

  pass {
    module.set("play-ahc-ws-standalone_2.11")
    group.set("com.typesafe.play")
    versions.set("[2.0.0,]")
    assertInverse.set(true)
  }

  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.12")
    versions.set("[2.0.0,2.1.0)")
    // 2.0.5 is missing play.shaded.ahc.org.asynchttpclient.AsyncHandler#onTlsHandshakeSuccess()V
    skip("2.0.5")
    assertInverse.set(true)
  }

  // No Scala 2.13 versions below 2.0.6 exist
  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.13")
    versions.set("[2.0.6,2.1.0)")
  }
}

val scalaVersion = "2.12"

dependencies {
  library("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:2.0.0")

  implementation(project(":instrumentation:play-ws:play-ws-common:javaagent"))

  testImplementation(project(":instrumentation:play-ws:play-ws-testing"))

  // These are to ensure cross compatibility
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:akka-actor-2.5:javaagent"))

  latestDepTestLibrary("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:2.0.+")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
}
