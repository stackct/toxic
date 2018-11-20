import * as vscode from 'vscode';

import { BaseNode, BaseNodeProvider } from './base';
import { DepNodeProvider } from './deps';
import { FunctionNodeProvider } from './functions';
import { TestNodeProvider } from './tests';

export function activate(context: vscode.ExtensionContext) {
    let depNodeProvider = new DepNodeProvider();
    let fnNodeProvider = new FunctionNodeProvider();
    let testNodeProvider = new TestNodeProvider();

    // Register TreeNodeProviders
    vscode.window.registerTreeDataProvider('pickle-deps', depNodeProvider);
    vscode.window.registerTreeDataProvider('pickle-functions', fnNodeProvider);
    vscode.window.registerTreeDataProvider('pickle-tests', testNodeProvider);

    // Register commands
    vscode.commands.registerCommand('pickleExplorer.openFile', (node: BaseNode) => node.openFile())

    // Set context value to enable display of extension icon
    vscode.commands.executeCommand('setContext', 'activated', true);
}

export function deactivate() {
}
