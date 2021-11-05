fileName = '/work/app/app.war'
appName = 'app'
contextRoot = '/app'
serverName = 'server1'

AdminApp.install(fileName,'[-appname ' + appName + ' -contextroot ' + contextRoot + ' -server ' + serverName + ' -usedefaultbindings ]')
AdminConfig.save()
