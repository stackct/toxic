#!/bin/bash

ROOT_DIR=`cd $(dirname "$0");pwd`
EXTENSION_DIR=${HOME}/.vscode/extensions
EXTENSION_PATH=${EXTENSION_DIR}/pickle

verbose=0

if [[ $1 == "-v" ]]; then
    verbose = 1
fi

if [[ ! -d ${EXTENSION_DIR} ]]; then
    echo "[ERROR] Extensions directory not found: ${EXTENSION_DIR}"
    exit 1;
fi

if [[ -L ${EXTENSION_DIR}/pickle ]]; then
    echo "[INFO] Found symlink to '${EXTENSION_PATH}', deleting."
    rm -f ${EXTENSION_PATH}
fi

echo -n "[INFO] Installing extension... "
cd ${ROOT_DIR}
npm run clean && npm install && npm run postinstall && npm run compile && ln -sf ${ROOT_DIR} ${EXTENSION_PATH}
exitcode=$?
echo "Done!"

exit $exitcode
