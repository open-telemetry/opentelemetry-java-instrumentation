plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[1.9.2, 2.1.2)")
    // except these versions, they are too old and the capabilities provided are not comprehensive.
    skip("1.7.0", "1.7.1", "1.7.2", "1.8.0", "1.8.1", "1.8.2", "1.9.0", "1.9.1")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.xuxueli:xxl-job-core:1.9.2")
  implementation(project(":instrumentation:xxl-job:xxl-job-common:javaagent"))

  testLibrary("com.xuxueli:xxl-job-core:1.9.2") {
    exclude("org.codehaus.groovy", "groovy")
  }
  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))
  latestDepTestLibrary("com.xuxueli:xxl-job-core:2.1.1") {
    exclude("org.codehaus.groovy", "groovy")
  }
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.xxl-job.experimental-span-attributes=true")
}
