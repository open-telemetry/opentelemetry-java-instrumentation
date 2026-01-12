plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[3,)")
    extraDependency("jakarta.el:jakarta.el-api:4.0.0")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  // can't use library for now because 6.1.0-M1 is latest and its POM referes to a missing parent POM
  // switch back to library when a new version is released
  // library("jakarta.el:jakarta.el-api:4.0.0")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")
  testImplementation("jakarta.el:jakarta.el-api:4.0.0")
  library("jakarta.faces:jakarta.faces-api:3.0.0")
  testLibrary("org.glassfish:jakarta.faces:3.0.4")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))
  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))

  // JSF 4+ requires CDI instead of BeanManager, the test should be upgraded first
  latestDepTestLibrary("jakarta.el:jakarta.el-api:4.+") // documented limitation
  latestDepTestLibrary("jakarta.faces:jakarta.faces-api:3.+") // documented limitation
  latestDepTestLibrary("org.glassfish:jakarta.faces:3.+") // documented limitation
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
    systemProperty("metadataConfig", "otel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
