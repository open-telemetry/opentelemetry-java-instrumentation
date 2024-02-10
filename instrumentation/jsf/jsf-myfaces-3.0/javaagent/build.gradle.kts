plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.myfaces.core")
    module.set("myfaces-impl")
    versions.set("[3,)")
    extraDependency("jakarta.el:jakarta.el-api:4.0.0")
    assertInverse.set(true)
  }
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

dependencies {
  library("jakarta.el:jakarta.el-api:4.0.0")
  library("org.apache.myfaces.core:myfaces-api:3.0.2")
  testLibrary("org.apache.myfaces.core:myfaces-impl:3.0.2")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))
  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))

  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))

  latestDepTestLibrary("jakarta.el:jakarta.el-api:4.+")
  latestDepTestLibrary("org.apache.myfaces.core:myfaces-api:3.+")
  latestDepTestLibrary("org.apache.myfaces.core:myfaces-impl:3.+")
  // JSF 4+ requires CDI instead of BeanManager, the test should be upgraded first
  // latestDepTestLibrary("org.apache.myfaces.core:myfaces-impl:4.+")
}

tasks {
  withType<Test>().configureEach {
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
  }
}
