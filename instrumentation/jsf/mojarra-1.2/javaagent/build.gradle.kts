plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[2.3.9,3)")
    extraDependency("javax.el:el-api:2.2")
  }
  pass {
    group.set("org.glassfish")
    module.set("javax.faces")
    versions.set("[2.0.7,3)")
    extraDependency("javax.el:el-api:2.2")
  }
  pass {
    group.set("com.sun.faces")
    module.set("jsf-impl")
    versions.set("[2.1,2.2)")
    extraDependency("javax.faces:jsf-api:2.1")
    extraDependency("javax.el:el-api:1.0")
  }
  pass {
    group.set("com.sun.faces")
    module.set("jsf-impl")
    versions.set("[2.0,2.1)")
    extraDependency("javax.faces:jsf-api:2.0")
    extraDependency("javax.el:el-api:1.0")
  }
  pass {
    group.set("javax.faces")
    module.set("jsf-impl")
    versions.set("[1.2,2)")
    extraDependency("javax.faces:jsf-api:1.2")
    extraDependency("javax.el:el-api:1.0")
  }
  fail {
    group.set("org.glassfish")
    module.set("jakarta.faces")
    versions.set("[3.0,)")
    extraDependency("javax.el:el-api:2.2")
  }
}

testSets {
  create("mojarra12Test")
  create("mojarra2Test")
  create("latestDepTest") {
    extendsFrom("mojarra2Test")
    dirName = "mojarra2LatestTest"
  }
}

tasks {
  test {
    dependsOn("mojarra12Test")
    dependsOn("mojarra2Test")
  }
}

dependencies {
  compileOnly("javax.faces:jsf-api:1.2")

  implementation(project(":instrumentation:jsf:jsf-common:library"))

  testImplementation(project(":instrumentation:jsf:jsf-testing-common"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  add("mojarra12TestImplementation", "javax.faces:jsf-impl:1.2-20")
  add("mojarra12TestImplementation", "javax.faces:jsf-api:1.2")
  add("mojarra12TestImplementation", "com.sun.facelets:jsf-facelets:1.1.14")

  add("mojarra2TestImplementation", "org.glassfish:jakarta.faces:2.3.12")

  add("latestDepTestImplementation", "org.glassfish:jakarta.faces:2.+")
}
