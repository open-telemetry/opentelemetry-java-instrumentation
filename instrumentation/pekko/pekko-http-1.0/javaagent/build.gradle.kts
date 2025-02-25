plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_2.12:1.0.1")
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_2.12:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_2.12:1.7.0")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_2.13:1.0.1")
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_2.13:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_2.13:1.7.0")
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-http_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
    extraDependency("org.apache.pekko:pekko-stream_3:1.0.1")
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_3:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_3:1.7.0")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.12")
    versions.set("[1.7,)")
    assertInverse.set(true)
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_2.12:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_2.12:1.7.0")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_2.13")
    versions.set("[1.7,)")
    assertInverse.set(true)
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_2.13:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_2.13:1.7.0")
  }
  pass {
    group.set("com.softwaremill.sttp.tapir")
    module.set("tapir-pekko-http-server_3")
    versions.set("[1.7,)")
    assertInverse.set(true)
    extraDependency("com.softwaremill.sttp.tapir:tapir-server_3:1.7.0")
    extraDependency("com.softwaremill.sttp.tapir:tapir-core_3:1.7.0")
  }
}

dependencies {
  library("org.apache.pekko:pekko-http_2.12:1.0.0")
  library("org.apache.pekko:pekko-stream_2.12:1.0.1")
  library("com.softwaremill.sttp.tapir:tapir-pekko-http-server_2.12:1.7.0")

  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:executors:javaagent"))

  latestDepTestLibrary("org.apache.pekko:pekko-http_2.13:latest.release")
  latestDepTestLibrary("org.apache.pekko:pekko-stream_2.13:latest.release")
  latestDepTestLibrary("com.softwaremill.sttp.tapir:tapir-pekko-http-server_2.13:latest.release")
}

tasks {
  withType<Test>().configureEach {
    // required on jdk17
    jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
    jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

    jvmArgs("-Dio.opentelemetry.javaagent.shaded.io.opentelemetry.context.enableStrictContext=false")

    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean)
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-http_2.12")
      exclude("org.apache.pekko", "pekko-stream_2.12")
      exclude("com.softwaremill.sttp.tapir", "tapir-pekko-http-server_2.12")
    }
  }
}
