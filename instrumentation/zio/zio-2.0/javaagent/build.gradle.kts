plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.scala-conventions")
}

val zioVersion = "2.0.0"
val scalaVersion = "2.12"

muzzle {
  pass {
    group.set("dev.zio")
    module.set("zio_2.12")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio_2.13")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("dev.zio")
    module.set("zio_3")
    versions.set("[$zioVersion,)")
    assertInverse.set(true)
  }
}

otelJava {
  maxJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  compileOnly("dev.zio:zio_$scalaVersion:$zioVersion")

  testImplementation("dev.zio:zio_$scalaVersion:$zioVersion")

  latestDepTestLibrary("dev.zio:zio_$scalaVersion:+")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")
  }
}
