#!/bin/bash

ROOT_DIR=`cd $(dirname "$0");pwd`
EXTENSION_ID=dotariel.pickle

exists() {
    code --list-extensions | grep ${EXTENSION_ID} 2>&1 >/dev/null
    echo $?
}

uninstall() {
    code --uninstall-extension ${EXTENSION_ID}
}

install() {
    cd ${ROOT_DIR}
    npm i vsce
    ./node_modules/.bin/vsce package --baseContentUrl https://dev/null --baseImagesUrl https://dev/null -o ${EXTENSION_ID}.vsix \
        && code --install-extension ${EXTENSION_ID}.vsix --force \
        && rm ${EXTENSION_ID}.vsix
    cd - 2>&1 >/dev/null
}

([[ $(exists) -eq 1 ]] || uninstall) && install
