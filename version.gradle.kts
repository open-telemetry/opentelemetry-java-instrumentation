allprojects {
  if (findProperty("otel.stable") != "true") {
    version = "1.13.0-alpha-SNAPSHOT"
  } else {
    version = "1.13.0-SNAPSHOT"
  }
}
