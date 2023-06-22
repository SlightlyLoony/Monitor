#! /bin/bash

# copy all the deployment files over to the Monitor server...
scp out/Monitor.jar beast:/apps/monitor
scp Monitor.service beast:/apps/monitor
scp configurationBeast.java beast:/apps/monitor/configuration.java
scp credentialsBeast.txt beast:/apps/monitor/credentials.txt
scp logging.properties beast:/apps/monitor

# execute commands on beast
# get to the app directory
# set mode and owner on files that stay in that directory
# copy the service wrapper (if it has changed) to the systemd/system directory, change its mode and owner
# bounce the events service
ssh beast << RUN_ON_BEAST
cd /apps/monitor
sudo chown tom:tom Monitor.jar
sudo chmod ug+xrw Monitor.jar
sudo chown tom:tom Monitor.service
sudo chmod ug+xrw Monitor.service
sudo chown tom:tom configuration.java
sudo chmod ug+xrw configuration.java
sudo chown tom:tom credentials.txt
sudo chmod ug+xrw credentials.txt
sudo chown tom:tom logging.properties
sudo chmod ug+xrw logging.properties
sudo cp -u Monitor.service /etc/systemd/system
sudo chown tom:tom /etc/systemd/system/Monitor.service
sudo chmod ug+xrw /etc/systemd/system/Monitor.service
sudo systemctl stop Monitor.service
sudo systemctl daemon-reload
sudo systemctl enable Monitor.service
sudo systemctl start Monitor.service
RUN_ON_BEAST
