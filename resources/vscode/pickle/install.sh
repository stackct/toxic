#!/bin/bash

ROOT_DIR=`cd $(dirname "$0");pwd`
EXTENSION_ID=dotariel.pickle
EDITOR=${EDITOR:-code}

get_editor_name() {
    case "${EDITOR}" in
        "code") echo "VS Code" ;;
        "cursor") echo "Cursor" ;;
        *) echo "${EDITOR}" ;;
    esac
}

exists() {
    ${EDITOR} --list-extensions | grep ${EXTENSION_ID} 2>&1 >/dev/null
    echo $?
}

uninstall() {
    echo "Uninstalling extension from $(get_editor_name)..."
    ${EDITOR} --uninstall-extension ${EXTENSION_ID}
}

install() {
    echo "Installing extension for $(get_editor_name)..."
    cd ${ROOT_DIR}
    yarn install
    ./node_modules/.bin/vsce package --baseContentUrl https://dev/null --baseImagesUrl https://dev/null -o ${EXTENSION_ID}.vsix \
        && ${EDITOR} --install-extension ${EXTENSION_ID}.vsix --force \
        && rm ${EXTENSION_ID}.vsix
    cd - 2>&1 >/dev/null
}

([[ $(exists) -eq 1 ]] || uninstall) && install
