plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba.nacos")
    module.set("nacos-client")
    versions.set("[2.0.3,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation("com.alibaba.nacos:nacos-client:2.0.3")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}


tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
