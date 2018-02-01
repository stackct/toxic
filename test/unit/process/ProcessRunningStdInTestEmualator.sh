#!/bin/sh
iterations=10
seconds=1
padding=1

if [[ $# -ge 1 ]]; then
  iterations=$1
fi

if [[ $# -ge 2 ]]; then
  seconds=$2
fi

if [[ $# -ge 3 ]]; then
  padding=$3
fi

lines=$iterations

echo "Iterations ${iterations}, seconds ${seconds}"
((lines=$lines-1))

while [[ lines -gt 0 ]]; do
  padding=`head -c $padding < /dev/zero | tr '\0' '#'`
  echo "${lines} ${padding} @"
  sleep $seconds
  ((lines=$lines-1))
done

exit 0




