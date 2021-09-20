import * as vscode from 'vscode'
import * as path from 'path'
import * as cp from 'child_process'
import { isFunction } from 'util'

export class Runtime {
  public static baseDir: string = path.join(vscode.workspace.rootPath, 'toxic', 'tests')
  private static command: string = 'toxic'
  private static outputChannel: vscode.OutputChannel

  public static runTest(name: string, path: string = this.baseDir, args: string[] = []) {
    let results = { success: 0, fail: 0 }
    let postOptions = (choice: string) => {
      if (choice == 'Rerun') Runtime.runTest(name, path, args)
    }

    let onData = (data: string) => {
      let match = data.match(
        /^.*\sAgent Shutdown; durationMS=(\d+); tasks=(\d+); success=(\d+); fail=(\d+).*/,
      )
      if (match != null && match.length > 0) {
        results.success = (<any>match[3]) as number
        results.fail = (<any>match[4]) as number
      }
    }

    let onError = (e: string) => {
      this.notifyFailure('Something went wrong; reason=' + e)
    }

    let onClose = (code: number, signal: string) => {
      let message = 'Test run completed; success:' + results.success + ', failed:' + results.fail
      let options = ['Dismiss', 'Rerun']

      if (code !== 0) {
        this.notifyFailure(message, options).then(postOptions)

        return
      }

      this.notifySuccess(message, options).then(postOptions)
    }

    this.run(path, args, onData, onError, onClose)
  }

  private static run(
    path: string,
    args: string[],
    data?: (d: string) => any,
    error?: (e: string) => any,
    close?: (c: number, s: string) => any,
  ) {
    Runtime.outputChannel =
      Runtime.outputChannel || vscode.window.createOutputChannel('Pickle Runtime')
    Runtime.outputChannel.clear()
    Runtime.outputChannel.show(true)

    let configArgs = vscode.workspace.getConfiguration().get('pickle.runtimeArgs') as string[]
    let allArgs = [`-doDir=${path}`].concat(args).concat(configArgs)

    let proc = cp.spawn(this.command, allArgs)

    proc.stdout.addListener('data', (chunk) => {
      let s = chunk.toString()
      Runtime.outputChannel.append(s)

      if (isFunction(data)) data(s)
    })

    proc.on('error', (e) => {
      this.notifyError(
        'Execution failed. Make sure ' + this.command + ' is installed in and your PATH',
      )
      if (isFunction(error)) error(e.message)
    })

    proc.on('close', (code, signal) => {
      if (isFunction(close)) close(code, signal)
    })
  }

  private static notifySuccess(message: string, options: string[] = []): Thenable<string> {
    let show = vscode.workspace.getConfiguration().get('pickle.notifications.success') as boolean
    if (show) {
      return vscode.window.showInformationMessage(message, ...options)
    }
  }

  private static notifyFailure(message: string, options: string[] = []): Thenable<string> {
    let show = vscode.workspace.getConfiguration().get('pickle.notifications.failure') as boolean
    if (show) {
      return vscode.window.showErrorMessage(message, ...options)
    }
  }

  private static notifyError(message: string, options: string[] = []): Thenable<string> {
    return vscode.window.showErrorMessage(message, ...options)
  }
}
