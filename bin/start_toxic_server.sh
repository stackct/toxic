#!/bin/bash -l
# ###########################
# Starts the toxic UI server 
# ###########################
if ! ps -ef | grep toxic-ui | grep -v grep > /dev/null 2>&1; then
  SEMAPHORE_FILE=$TOXIC_HOME/conf/no_restart
  if [[ ! -f "${SEMAPHORE_FILE}" ]]; then
    logger -t "toxic-watchdog" "Toxic-ui is not running. Restarting..."
    nohup $TOXIC_HOME/bin/toxic-ui -j $TOXIC_DATA/jobs -p jobs/toxic.properties -s $TOXIC_HOME/conf/toxic-secure.properties >> $TOXIC_HOME/log/nohup.out 2>&1 &
    logger -t "toxic-watchdog" "Toxic-ui restarted"
  else
    logger -t "toxic-watchdog" "Toxic-ui is not running, but not restarting because ${SEMAPHORE_FILE} exists, since $(stat -c %y ${SEMAPHORE_FILE})"
  fi
fi