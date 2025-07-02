plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.commons")
    module.set("commons-dbcp2")
    versions.set("[2,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.commons:commons-dbcp2:2.0")

  implementation(project(":instrumentation:apache-dbcp-2.0:library"))

  testImplementation(project(":instrumentation:apache-dbcp-2.0:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")

    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metaDataConfig", "otel.semconv-stability.opt-in=database")
    systemProperty("collectSpans", true)
  }

  test {
    systemProperty("collectMetadata", collectMetadata)
    systemProperty("collectSpans", true)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
