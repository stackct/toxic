import * as vscode from 'vscode';

import { DepNodeProvider } from './deps';
import { FunctionNodeProvider } from './functions';
import { TestNodeProvider } from './tests';


export function activate(context: vscode.ExtensionContext) {
    const depNodeProvider = new DepNodeProvider(vscode.workspace.rootPath);
    vscode.window.registerTreeDataProvider('pickle-deps', depNodeProvider);

    const functionNodeProvider = new FunctionNodeProvider();
    vscode.window.registerTreeDataProvider('pickle-functions', functionNodeProvider);

    const testNodeProvider = new TestNodeProvider();
    vscode.window.registerTreeDataProvider('pickle-tests', testNodeProvider);

    vscode.commands.executeCommand('setContext', 'hasPickleItems', true);
}

export function deactivate() {
}

