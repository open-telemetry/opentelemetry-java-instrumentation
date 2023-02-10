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

testSets {
  create("myfaces3Test")
  create("myfaces4Test")
}

tasks {
  test {
    dependsOn("myfaces3Test")
    dependsOn("myfaces4Test")
  }
}

dependencies {
  compileOnly("org.apache.myfaces.core:myfaces-api:3.0.2")
  compileOnly("jakarta.el:jakarta.el-api:4.0.0")

  implementation(project(":instrumentation:jsf:jsf-jakarta-common:javaagent"))

  testImplementation(project(":instrumentation:jsf:jsf-jakarta-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-common:javaagent"))

  add("myfaces3TestImplementation", "org.apache.myfaces.core:myfaces-impl:3.0.2")

  add("myfaces4TestImplementation", "org.apache.myfaces.core:myfaces-impl:4.0.0-RC4")
}
