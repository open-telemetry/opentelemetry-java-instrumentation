plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.nifi")
    module.set("nifi-framework-core")
    versions.set("[1.14.0, 1.24.0)")
  }
  pass {
    group.set("org.apache.nifi")
    module.set("nifi-standard-processors")
    versions.set("[1.14.0, 1.24.0)")
  }
  pass {
    group.set("org.apache.nifi")
    module.set("nifi-bin-manager")
    versions.set("[1.14.0, 1.24.0)")
  }
  pass {
    group.set("com.squareup.okhttp3")
    module.set("okhttp3")
    versions.set("[4.10.0,)")
  }
  pass {
    group.set("javax.servlet")
    module.set("javax.servlet-api")
    versions.set("[3.1.0,)")
  }
}

dependencies {

  library("org.apache.nifi:nifi-framework-core:1.24.0")
  library("org.apache.nifi:nifi-bin-manager:1.24.0")
  library("org.apache.nifi:nifi-standard-processors:1.24.0")
  library("javax.servlet:javax.servlet-api:3.1.0")
  library("com.squareup.okhttp3:okhttp:4.10.0")

  implementation(project(":instrumentation:apache-nifi:apache-nifi-1.24.0:library"))
  testImplementation(project(":instrumentation:apache-nifi:apache-nifi-1.24.0:testing"))
}

tasks.withType<JavaCompile>().configureEach {
  with(options) {
    release.set(17)
  }
}