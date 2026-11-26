plugins {
  id("otel.library-instrumentation")
}

base.archivesName.set("${base.archivesName.get()}-autoconfigure")

dependencies {
  implementation(project(":instrumentation:apache-dubbo:apache-dubbo-3.1:library"))

  library("org.apache.dubbo:dubbo:3.1.0")

  testImplementation(project(":instrumentation:apache-dubbo:testing")) {
    exclude(group = "org.apache.dubbo")
  }
  testLibrary("org.apache.dubbo:dubbo-config-api:3.1.0")
  testRuntimeOnly("org.apache.curator:curator-x-discovery:5.9.0")
  if (otelProps.testLatestDeps) {
    testImplementation("org.apache.dubbo:dubbo-rpc-triple:latest.release")
    // these dependencies should be optional but when running on jdk25 more classes are loaded than
    // on earlier jdk versions and DubboRegistryTest fails with NoClassDefFoundError
    testImplementation("com.alibaba:fastjson:2.0.65")
    testImplementation("com.google.code.gson:gson:2.14.0")
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps)
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  // to suppress non-fatal errors on jdk17
  jvmArgs("--add-opens=java.base/java.math=ALL-UNNAMED")
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
}
