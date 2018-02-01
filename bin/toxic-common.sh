#!/bin/bash

if [ -z "${TOXIC_HOME}" ]; then
    bin=`dirname $0`
    if [[ "." == $bin ]]; then
      bin="$PWD"
    fi
    TOXIC_HOME=`dirname $bin`
fi

classpath=""
doDir=""
otherArgs=""

while [[ $# != 0 ]]; do
  arg=$1
  equalPos=`expr "$arg" : "[-a-zA-Z]*="`
    prompt=${arg:0:$equalPos}
    value=${arg:$equalPos}

  if [[ $prompt == "-cp=" ]]; then
    classpath=$value
  else
    otherArgs="$otherArgs $arg"
  fi

  shift
done

classpath="$classpath:/conf"
classpath="$classpath:${TOXIC_HOME}/conf"
classpath="$classpath:${TOXIC_HOME}/resources"
classpath="$classpath:${TOXIC_HOME}/gen/classes"
classpath="$classpath:${TOXIC_HOME}/lib/*"

if [ -n "${TOXIC_HEAP_MAX}" ]; then
  TOXIC_JRE_OPTIONS="${TOXIC_JRE_OPTIONS} -Xmx${TOXIC_HEAP_MAX}"
fi
if [ -n "${TOXIC_PERMGEN_MAX}" ]; then
  TOXIC_JRE_OPTIONS="${TOXIC_JRE_OPTIONS} -XX:MaxPermSize=${TOXIC_PERMGEN_MAX}"
fi

baseToxicCmd="java -XX:+CMSClassUnloadingEnabled $TOXIC_JRE_OPTIONS -cp $classpath -DTOXIC_HOME=${TOXIC_HOME}"