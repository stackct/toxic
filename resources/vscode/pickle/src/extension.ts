import * as vscode from 'vscode'

import { DepNodeProvider } from './deps'
import { FunctionNodeProvider } from './functions'
import { TestNodeProvider } from './tests'

import { goToFunction, goToLibrary, openFile, runAll, runFileTests, runTest } from './commands'
import {
  RunTestCodeLensProvider,
  GoToFunctionCodeLensProvider,
  RunTestFileCodeLensProvider,
  GoToLibraryCodeLensProvider,
} from './code-lens-providers'
import { PickleFileDocumentSelector } from './pickle-file-selector'
import { PickleDepsCompletionProvider, PickleLocalCompletionProvider } from './pickle-completion-provider'

const disposables: vscode.Disposable[] = []

export function activate(context: vscode.ExtensionContext) {
  // Register TreeNodeProviders
  registerTreeDataProvider('pickle-deps', new DepNodeProvider())
  registerTreeDataProvider('pickle-functions', new FunctionNodeProvider())
  registerTreeDataProvider('pickle-tests', new TestNodeProvider())
  registerCompletionProvider('plaintext', new PickleLocalCompletionProvider()),
  registerCompletionProvider('groovy', new PickleLocalCompletionProvider()),
  registerCompletionProvider('plaintext', new PickleDepsCompletionProvider()),
  registerCompletionProvider('groovy', new PickleDepsCompletionProvider()),

  // Register commands
  context.subscriptions.push(
    registerCommand('pickle.openFile', openFile),
    registerCommand('pickle.runAll', runAll),
    registerCommand('pickle.runFileTests', runFileTests),
    registerCommand('pickle.runTest', runTest),
    registerCommand('pickle.goToFunction', goToFunction),
    registerCommand('pickle.goToLibrary', goToLibrary),
    registerCodeLensProvider(new PickleFileDocumentSelector(), new RunTestCodeLensProvider()),
    registerCodeLensProvider(new PickleFileDocumentSelector(), new RunTestFileCodeLensProvider()),
    registerCodeLensProvider(new PickleFileDocumentSelector(), new GoToFunctionCodeLensProvider()),
    registerCodeLensProvider(new PickleFileDocumentSelector(), new GoToLibraryCodeLensProvider()),
  )

  // Set context value to enable display of extension icon
  vscode.commands.executeCommand('setContext', 'activated', true)
}

function registerCommand(id: string, callback: (...args: any[]) => any): vscode.Disposable {
  const disposable = vscode.commands.registerCommand(id, callback)
  disposables.push(disposable)

  return disposable
}

function registerCodeLensProvider(
  selector: vscode.DocumentSelector,
  provider: vscode.CodeLensProvider,
): vscode.Disposable {
  const disposable = vscode.languages.registerCodeLensProvider(selector, provider)
  disposables.push(disposable)

  return disposable
}

function registerTreeDataProvider(
  id: string,
  provider: vscode.TreeDataProvider<any>,
): vscode.Disposable {
  const disposable = vscode.window.registerTreeDataProvider(id, provider)
  disposables.push(disposable)

  return disposable
}

function registerCompletionProvider(
  id: string,
  provider: vscode.CompletionItemProvider<any>
): vscode.Disposable {

  const trigger = vscode.workspace.getConfiguration().get('pickle.codecompletion.trigger') as string

  const disposable = vscode.languages.registerCompletionItemProvider(id, provider, trigger)
  disposables.push(disposable)

  return disposable
}

export function deactivate() {
  disposables.forEach((d) => d.dispose())
}
