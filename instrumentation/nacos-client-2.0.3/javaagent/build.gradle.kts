plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.alibaba.nacos")
    module.set("nacos-client")
    versions.set("[2.0.3,)")
    skip("0.5.0", "0.6.1", "1.1.2", "1.1.4", "1.4.7", "2.0.1", "2.0.2")
    assertInverse.set(true)
  }
}

dependencies {
  val nacosClientVersion = "2.0.3"
  implementation("com.alibaba.nacos:nacos-client:$nacosClientVersion")
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
