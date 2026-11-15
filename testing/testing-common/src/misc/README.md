# Miscellaneous

testing-keystore.p12 generated with:

```
keytool -genkeypair -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore testing-keystore.p12 -validity 3650 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1"

password = testing
```
