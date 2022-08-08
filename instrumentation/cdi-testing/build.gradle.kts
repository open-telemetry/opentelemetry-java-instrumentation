plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testLibrary("org.jboss.weld:weld-core:2.3.0.Final")
  testLibrary("org.jboss.weld.se:weld-se:2.3.0.Final")
  testLibrary("org.jboss.weld.se:weld-se-core:2.3.0.Final")
}
