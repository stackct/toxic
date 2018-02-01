#!/bin/bash

# Configure timezone
rm /etc/localtime && ln -s /usr/share/zoneinfo/GMT /etc/localtime

# Modify SSH config
echo -e '\nGatewayPorts clientspecified' >> /etc/ssh/sshd_config
service sshd restart

# Place all custom product/data onto large partition
mkdir /media/ephemeral0/opt
ln -s /media/ephemeral0/opt /opt
mkdir /media/ephemeral0/docker
ln -s /media/ephemeral0/docker /var/lib/docker

# Install repository and packages
# TBD - depends on team's packaging framework: yum, apt, etc

mkdir /etc/docker
echo -e '{ "live-restore": true, "storage-driver": "overlay2" }' > /etc/docker/daemon.json
result=1
while [ $result -ne 0 ]; do
  yum install -y --enablerepo=epel ansible toxic docker
  id toxic
  result=$?
done
service docker start

# Create credentials:
mkdir ~toxic/.aws
echo "[default]" >> ~toxic/.aws/credentials
echo "aws_access_key_id = ${access_key_id}" >> ~toxic/.aws/credentials
echo "aws_secret_access_key = ${secret_access_key}" >> ~toxic/.aws/credentials

echo "[default]" >> ~toxic/.aws/config
echo "output=json" >> ~toxic/.aws/config
echo "region=${region}" >> ~toxic/.aws/config

chown -R toxic:toxic ~toxic

# Wait signal
touch /tmp/signal