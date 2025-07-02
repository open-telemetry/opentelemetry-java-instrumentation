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
