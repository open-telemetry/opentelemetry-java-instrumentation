plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.xuxueli")
    module.set("xxl-job-core")
    versions.set("[2.1.2,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.xuxueli:xxl-job-core:2.1.2")
  implementation(project(":instrumentation:xxl-job:xxl-job-common:javaagent"))

  testLibrary("com.xuxueli:xxl-job-core:2.1.2") {
    exclude("org.codehaus.groovy", "groovy")
  }
  testImplementation(project(":instrumentation:xxl-job:xxl-job-common:testing"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.xxl-job.experimental-span-attributes=true")
}
