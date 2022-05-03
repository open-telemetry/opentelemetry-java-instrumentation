plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.dubbo")
    module.set("dubbo")
    versions.set("[2.7.0,3.0.0)")
  }
}

dependencies {
  implementation(project(":instrumentation:apache-dubbo-2.7:library-autoconfigure"))

  library("org.apache.dubbo:dubbo:2.7.0")

  testImplementation(project(":instrumentation:apache-dubbo-2.7:testing"))

  testLibrary("org.apache.dubbo:dubbo-config-api:2.7.0")
  latestDepTestLibrary("org.apache.dubbo:dubbo:2.+") // documented limitation
  latestDepTestLibrary("org.apache.dubbo:dubbo-config-api:2.+") // documented limitation
}

tasks.withType<Test>().configureEach {
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
}
