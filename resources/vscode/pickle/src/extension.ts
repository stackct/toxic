import * as vscode from 'vscode';

import { BaseNode } from './base';
import { DepNodeProvider } from './deps';
import { FunctionNodeProvider } from './functions';
import { TestNodeProvider } from './tests';


export function activate(context: vscode.ExtensionContext) {
    vscode.window.registerTreeDataProvider('pickle-deps', new DepNodeProvider());
    vscode.window.registerTreeDataProvider('pickle-functions', new FunctionNodeProvider());
    vscode.window.registerTreeDataProvider('pickle-tests', new TestNodeProvider());
    vscode.commands.registerCommand('pickleExplorer.openFile', (node: BaseNode) => node.openFile())
    vscode.commands.executeCommand('setContext', 'hasPickleItems', true);
}

export function deactivate() {
}
