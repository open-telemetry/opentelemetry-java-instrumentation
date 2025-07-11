plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.axis2")
    module.set("axis2-jaxws")
    versions.set("[1.6.0,)")
    assertInverse.set(true)
    // version 1.2 depends on org.apache.axis2:axis2-kernel:1.2
    // which depends on org.apache.neethi:neethi:2.0.1 which does not exist
    // version 1.3 depends on org.apache.axis2:axis2-kernel:1.3
    // which depends on org.apache.woden:woden:1.0-incubating-M7b which does not exist
    skip("1.2", "1.3")
  }
}

dependencies {
  bootstrap(project(":instrumentation:servlet:servlet-common:bootstrap"))

  library("org.apache.axis2:axis2-jaxws:1.6.0")

  compileOnly(project(":muzzle"))
  compileOnly("jakarta.servlet:jakarta.servlet-api:5.0.0")
}
