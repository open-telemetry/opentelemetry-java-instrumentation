plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.shardingsphere.elasticjob")
    module.set("elasticjob-executor-kernel")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.shardingsphere.elasticjob:elasticjob-executor-kernel:3.0.0")
  library("org.apache.shardingsphere.elasticjob:elasticjob-dataflow-executor:3.0.0")
  library("org.apache.shardingsphere.elasticjob:elasticjob-simple-executor:3.0.0")

  testLibrary("org.apache.shardingsphere.elasticjob:elasticjob-lite-core:3.0.0")
  testImplementation("org.apache.curator:curator-test:5.1.0")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.elasticjob.experimental-span-attributes=true")
}
