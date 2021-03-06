#!/bin/bash

if [ -z "${TOXIC_HOME}" ]; then
    bin=`dirname $0`
    if [[ "." == $bin ]]; then
      bin="$PWD"
    fi
    TOXIC_HOME=`dirname $bin`
fi

classpath=""
classpathSeparator=":"
doDir=""
otherArgs=""

if [ $OSTYPE == 'msys' ]; then
  classpathSeparator=";"
fi

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

if [ -n "${classpath}" ]; then
  classpath="${classpath}${classpathSeparator}"
fi

classpath="${classpath}/conf"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}/conf"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}/resources"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}/gen/classes"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}/gen/build/classes"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}/lib/*"
classpath="${classpath}${classpathSeparator}${TOXIC_HOME}"

if [ -n "${TOXIC_HEAP_MAX}" ]; then
  TOXIC_JRE_OPTIONS="${TOXIC_JRE_OPTIONS} -Xmx${TOXIC_HEAP_MAX}"
fi
if [ -n "${TOXIC_PERMGEN_MAX}" ]; then
  TOXIC_JRE_OPTIONS="${TOXIC_JRE_OPTIONS} -XX:MaxPermSize=${TOXIC_PERMGEN_MAX}"
fi

baseToxicCmd="java -XX:+CMSClassUnloadingEnabled $TOXIC_JRE_OPTIONS -cp '${classpath}' -DTOXIC_HOME=${TOXIC_HOME}"
