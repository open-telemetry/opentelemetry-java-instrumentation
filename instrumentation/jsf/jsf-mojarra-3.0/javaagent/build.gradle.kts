plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
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
  compileOnly("jakarta.faces:jakarta.faces-api:3.0.0")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))

  testImplementation("org.glassfish:jakarta.faces:3.0.4")
  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))
}
