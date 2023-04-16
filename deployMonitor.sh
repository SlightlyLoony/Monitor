#! /bin/bash

# copy all the deployment files over to the Events server...
scp out/EmailService.jar beast:/apps/emailservice
scp EmailService.service beast:/apps/emailservice
scp configuration.java beast:/apps/emailservice
scp credentials.txt beast:/apps/emailservice
scp logging.properties beast:/apps/emailservice

# execute commands on beast
# get to the app directory
# set mode and owner on files that stay in that directory
# copy the service wrapper (if it has changed) to the systemd/system directory, change its mode and owner
# bounce the events service
ssh beast << RUN_ON_BEAST
cd /apps/emailservice
sudo chown tom:tom EmailService.jar
sudo chmod ug+xrw EmailService.jar
sudo chown tom:tom EmailService.service
sudo chmod ug+xrw EmailService.service
sudo chown tom:tom configuration.java
sudo chmod ug+xrw configuration.java
sudo chown tom:tom credentials.txt
sudo chmod ug+xrw credentials.txt
sudo chown tom:tom logging.properties
sudo chmod ug+xrw logging.properties
sudo cp -u EmailService.service /etc/systemd/system
sudo chown tom:tom /etc/systemd/system/EmailService.service
sudo chmod ug+xrw /etc/systemd/system/EmailService.service
sudo systemctl stop EmailService.service
sudo systemctl daemon-reload
sudo systemctl enable EmailService.service
sudo systemctl start EmailService.service
RUN_ON_BEAST
