plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.mchange")
    module.set("c3p0")
    versions.set("(,)")
    // these versions have missing dependencies in maven central
    skip("0.9.2-pre2-RELEASE", "0.9.2-pre3")
  }
}

dependencies {
  // first non pre-release version available on maven central
  library("com.mchange:c3p0:0.9.2")

  implementation(project(":instrumentation:c3p0-0.9:library"))

  testImplementation(project(":instrumentation:c3p0-0.9:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
  }

  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
