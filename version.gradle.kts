val stableVersion = "2.20.0"
val alphaVersion = "2.20.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
