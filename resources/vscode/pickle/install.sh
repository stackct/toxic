#!/bin/bash

ROOT_DIR=`cd $(dirname "$0");pwd`
EXTENSION_DIR=${HOME}/.vscode/extensions
EXTENSION_PATH=${EXTENSION_DIR}/pickle

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
rm -fr ${ROOT_DIR}/out
npm install > /dev/null 2>&1 && npm run compile > /dev/null 2>&1 && ln -sf ${ROOT_DIR} ${EXTENSION_PATH}
exitcode=$?
echo "Done!"

exit $exitcode
