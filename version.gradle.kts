allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.10.0-alpha-SNAPSHOT"
  } else {
    version = "1.10.0-SNAPSHOT"
  }
}
