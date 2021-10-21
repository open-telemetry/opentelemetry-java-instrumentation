plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
}

muzzle {
  pass {
    group.set("org.apache.myfaces.core")
    module.set("myfaces-impl")
    versions.set("[1.2,3)")
    extraDependency("jakarta.el:jakarta.el-api:3.0.3")
    assertInverse.set(true)
  }
}

testSets {
  create("myfaces12Test")
  create("myfaces2Test")
  create("latestDepTest") {
    extendsFrom("myfaces2Test")
    dirName = "myfaces2LatestTest"
  }
}

tasks {
  test {
    dependsOn("myfaces12Test")
    dependsOn("myfaces2Test")
  }
}

dependencies {
  compileOnly("org.apache.myfaces.core:myfaces-api:1.2.12")
  compileOnly("javax.el:el-api:1.0")

  implementation(project(":instrumentation:jsf:jsf-common:library"))

  testImplementation(project(":instrumentation:jsf:jsf-common:testing"))
  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  add("myfaces12TestImplementation", "org.apache.myfaces.core:myfaces-impl:1.2.12")
  add("myfaces12TestImplementation", "com.sun.facelets:jsf-facelets:1.1.14")

  add("myfaces2TestImplementation", "org.apache.myfaces.core:myfaces-impl:2.3.2")
  add("myfaces2TestImplementation", "javax.xml.bind:jaxb-api:2.2.11")
  add("myfaces2TestImplementation", "com.sun.xml.bind:jaxb-impl:2.2.11")

  add("latestDepTestImplementation", "org.apache.myfaces.core:myfaces-impl:2.+")
}
