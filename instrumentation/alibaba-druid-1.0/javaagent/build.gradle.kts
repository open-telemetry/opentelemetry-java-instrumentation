plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba")
    module.set("druid")
    versions.set("(,)")
    skip("1.0.30")
  }
}

dependencies {
  library("com.alibaba:druid:1.0.0")

  implementation(project(":instrumentation:alibaba-druid-1.0:library"))

  testImplementation(project(":instrumentation:alibaba-druid-1.0:testing"))
}

tasks {
  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  test {
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
