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

testSets {
  create("mojarra3Test")
  create("mojarra4Test")
}

tasks {
  test {
    dependsOn("mojarra3Test")
    dependsOn("mojarra4Test")
  }
}

dependencies {
  compileOnly("jakarta.faces:jakarta.faces-api:3.0.0")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))

  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))

  add("mojarra3TestImplementation", "org.glassfish:jakarta.faces:3.0.4")

  add("mojarra4TestImplementation", "org.glassfish:jakarta.faces:4.0.1")
}
