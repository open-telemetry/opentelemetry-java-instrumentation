plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba.nacos")
    module.set("nacos-client")
    versions.set("[2.0.0,3.0.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.alibaba.nacos:nacos-client:2.0.0")
  latestDepTestLibrary("com.alibaba.nacos:nacos-client:2.+")

  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))

  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("io.opentelemetry:opentelemetry-context")
  testImplementation("com.alibaba.nacos:nacos-client:2.0.0")
}

tasks.withType<Test>().configureEach {
  systemProperty("collectMetadata", otelProps.collectMetadata)
}

afterEvaluate {
  tasks.withType<Test>().configureEach {
    classpath += sourceSets.main.get().output
  }
}
