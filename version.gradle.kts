val stableVersion = "2.19.0"
val alphaVersion = "2.19.0-alpha"

allprojects {
  if (findProperty("otel.stable") != "true") {
    version = alphaVersion
  } else {
    version = stableVersion
  }
}
