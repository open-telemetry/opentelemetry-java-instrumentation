val snapshot = true

allprojects {
  var v = "1.13.0"
  if (findProperty("otel.stable") != "true") {
    v += "-alpha"
  }
  if (snapshot) {
    v += "-SNAPSHOT"
  }
  version = v
}
