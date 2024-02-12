plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.mybatis")
    module.set("mybatis")
    versions.set("[3.2.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.mybatis:mybatis:3.2.0")

  testImplementation("com.h2database:h2:1.4.191")
}

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.mybatis.enabled=true")

  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
