:: server is run through a launcher process, to add jvm arguments we need to add them to configuration file
:: firstly split JVM_ARGS environment variable by space character and put each argument into <jvm-options> tag
:: after that place options into configuration xml after <jvm-options>-server</jvm-options>
SET IN_CONF_FILE=/server/glassfish/domains/domain1/config/domain.xml
SET OUT_CONF_FILE=/server/glassfish/domains/domain1/config/domain.xml
powershell -command "$opts='<jvm-options>' + $env:JVM_ARGS + '</jvm-options>'; $opts=$opts -replace ' ', '</jvm-options><jvm-options>'; (gc $env:IN_CONF_FILE) -replace '<jvm-options>-server</jvm-options>', ('<jvm-options>-server</jvm-options>' + $opts) | sc $env:OUT_CONF_FILE"
:: --verbose starts server in foreground mode where output is printed to console
java -jar glassfish/lib/client/appserver-cli.jar start-domain --verbose domain1
