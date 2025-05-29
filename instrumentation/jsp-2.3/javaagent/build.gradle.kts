plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.tomcat")
    module.set("tomcat-jasper")
    // tomcat 10 uses JSP 3.0
    versions.set("[7.0.19,10)")
    // version 8.0.9 depends on org.eclipse.jdt.core.compiler:ecj:4.4RC4 which does not exist
    skip("8.0.9")
    // not using assertInverse.set(true) because org.eclipse.jdt.core.compiler:ecj:xxx dependency
    // is missing for versions in range [7.0.0, 7.0.19)
  }
  fail {
    group.set("org.apache.tomcat")
    module.set("tomcat-jasper")
    versions.set("[,7.0.0)")
  }
  fail {
    group.set("org.apache.tomcat")
    module.set("tomcat-jasper")
    versions.set("[10,)")
  }
}

dependencies {
  // compiling against tomcat 7.0.20 because there seems to be some issues with Tomcat's dependency < 7.0.20
  compileOnly("org.apache.tomcat:tomcat-jasper:7.0.20")
  library("javax.servlet.jsp:javax.servlet.jsp-api:2.3.0")
  library("javax.servlet:javax.servlet-api:3.1.0")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:servlet:servlet-javax-common:javaagent"))

  // using tomcat 7.0.37 because there seems to be some issues with Tomcat's jar scanning in versions < 7.0.37
  // https://stackoverflow.com/questions/23484098/org-apache-tomcat-util-bcel-classfile-classformatexception-invalid-byte-tag-in
  testLibrary("org.apache.tomcat.embed:tomcat-embed-core:7.0.37")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-logging-juli:7.0.37")
  testLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:7.0.37")

  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-core:9.+") // documented limitation
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-jasper:9.+") // documented limitation
  latestDepTestLibrary("org.apache.tomcat.embed:tomcat-embed-logging-juli:9.+") // documented limitation
}

tasks.withType<Test>().configureEach {
  // skip jar scanning using environment variables:
  // http://tomcat.apache.org/tomcat-7.0-doc/config/systemprops.html#JAR_Scanning
  // having this set allows us to test with old versions of the tomcat api since
  // JarScanFilter did not exist in the tomcat 7 api
  jvmArgs("-Dorg.apache.catalina.startup.ContextConfig.jarsToSkip=*")
  jvmArgs("-Dorg.apache.catalina.startup.TldConfig.jarsToSkip=*")

  // required on jdk17
  jvmArgs("--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  // TODO run tests both with and without experimental span attributes
  jvmArgs("-Dotel.instrumentation.jsp.experimental-span-attributes=true")

  jvmArgs("-Dotel.instrumentation.common.experimental.view-telemetry.enabled=true")
}
