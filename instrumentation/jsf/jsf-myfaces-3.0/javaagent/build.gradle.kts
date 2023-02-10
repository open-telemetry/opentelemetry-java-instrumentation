plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
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

dependencies {
  compileOnly("org.apache.myfaces.core:myfaces-api:3.0.2")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))

  testImplementation("org.apache.myfaces.core:myfaces-impl:3.0.2")
  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))
}
