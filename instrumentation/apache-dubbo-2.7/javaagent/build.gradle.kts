plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.dubbo")
    module.set("dubbo")
    versions.set("[2.7,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:apache-dubbo-2.7:library-autoconfigure"))

  library("org.apache.dubbo:dubbo:2.7.5")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))
}

tasks.withType<Test>().configureEach {
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
