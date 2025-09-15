plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.vibur")
    module.set("vibur-dbcp")
    versions.set("[11.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.vibur:vibur-dbcp:11.0")

  implementation(project(":instrumentation:vibur-dbcp-11.0:library"))

  testImplementation(project(":instrumentation:vibur-dbcp-11.0:testing"))
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("collectMetadata", collectMetadata)
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testStableSemconv)
  }
}
