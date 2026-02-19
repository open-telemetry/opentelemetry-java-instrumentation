plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.scala-conventions")
}

val zioVersion = "3.0.0"
val scalaVersion = "2.12"

muzzle {
  pass {
    group.set("dev.zio")
    module.set("zio-http_2.12")
    versions.set("[$zioVersion,)")
    // don't care about these versions
    skip("0.0.1", "0.0.2", "0.0.3", "0.0.4", "0.0.5")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio-http_2.13")
    versions.set("[$zioVersion,)")
    // don't care about these versions
    skip("0.0.1", "0.0.2", "0.0.3", "0.0.4", "0.0.5")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio-http_3")
    versions.set("[$zioVersion,)")
    // don't care about these versions
    skip("0.0.1", "0.0.2", "0.0.3", "0.0.4", "0.0.5")
    assertInverse.set(true)
  }
}

dependencies {
  library("dev.zio:zio-http_$scalaVersion:$zioVersion")
  testCompileOnly("org.scala-lang:scala-library:2.12.20")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}
