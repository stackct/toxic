import * as vscode from 'vscode'

import { DepNodeProvider } from './deps'
import { FunctionNodeProvider } from './functions'
import { TestNodeProvider } from './tests'
import { Runtime } from './runtime'
import { BaseNode } from './base-node'

export function activate(context: vscode.ExtensionContext) {
  // Register TreeNodeProviders
  vscode.window.registerTreeDataProvider('pickle-deps', new DepNodeProvider())
  vscode.window.registerTreeDataProvider('pickle-functions', new FunctionNodeProvider())
  vscode.window.registerTreeDataProvider('pickle-tests', new TestNodeProvider())

  // Register commands
  vscode.commands.registerCommand('pickleExplorer.openFile', (node: BaseNode) => node.openFile())
  vscode.commands.registerCommand('pickleExplorer.runAll', () => Runtime.runTest('all'))

  // Set context value to enable display of extension icon
  vscode.commands.executeCommand('setContext', 'activated', true)
}

export function deactivate() {}
