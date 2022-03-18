plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.ning")
    module.set("async-http-client")
    versions.set("[1.9.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.ning:async-http-client:1.9.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-exports=java.base/sun.security.util=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
