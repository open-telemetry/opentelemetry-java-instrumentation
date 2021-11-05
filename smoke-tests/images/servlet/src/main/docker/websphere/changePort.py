# change port from 9080 to 88080
AdminTask.modifyServerPort('server1', '[-nodeName ' + AdminControl.getNode() + ' -endPointName WC_defaulthost -host * -port 8080 -modifyShared true]')
# add new port to default virtual host
AdminConfig.create('HostAlias', AdminConfig.getid('/Cell:' + AdminControl.getCell() + '/VirtualHost:default_host/'), '[[port "8080"] [hostname "*"]]')
AdminConfig.save()