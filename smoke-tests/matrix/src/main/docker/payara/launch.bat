java -jar glassfish/lib/client/appserver-cli.jar start-domain domain1
powershell -command "Get-Content /server/glassfish/domains/domain1/logs/server.log -Wait"
