import * as vscode from 'vscode'
import * as path from 'path'
import * as cp from 'child_process'

export class Runtime {
  public static baseDir: string = path.join(vscode.workspace.rootPath, 'toxic', 'tests')
  private static command: string = 'toxic'
  private static outputChannel: vscode.OutputChannel
  private static outputTerminal: vscode.Terminal
  private static outputPty: vscode.Pseudoterminal
  private static terminalEmitter: vscode.EventEmitter<string>

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

    const enableTerminal = vscode.workspace.getConfiguration().get('pickle.terminal.enable') as boolean

    if(enableTerminal && !Runtime.outputTerminal){
      const writeEmitter = new vscode.EventEmitter<string>();
      const pty: vscode.Pseudoterminal = {
        onDidWrite: writeEmitter.event,
        open: () => this.logTerminal(`\x1b[32mrunning: ${this.command} ${allArgs.join(' ')}`),
        close: () => Runtime.outputTerminal = null,
        setDimensions: dimen => { dimen = { rows: 100000000, columns: 500}}
      };

      Runtime.outputPty = pty
      Runtime.terminalEmitter = writeEmitter
      Runtime.outputTerminal = vscode.window.createTerminal({ name: 'Pickle Runtime', pty }) 
    }

    let configArgs = vscode.workspace.getConfiguration().get('pickle.runtimeArgs') as string[]
    let allArgs = [`-doDir=${path}`].concat(args).concat(configArgs)

    let proc = cp.spawn(this.command, allArgs, {
      cwd: vscode.workspace.workspaceFolders[0].uri.path,
    })

    if(enableTerminal){
      Runtime.outputTerminal.show(true)
      this.logTerminal(`\x1b[32mrunning: ${this.command} ${allArgs.join(' ')}`)
    } else {
      Runtime.outputChannel.clear()
      Runtime.outputChannel.show(true)
      Runtime.outputChannel.appendLine(`running: ${this.command} ${allArgs.join(' ')}`)
    }

    proc.stdout.addListener('data', (chunk) => {
      let s = chunk.toString()

      if(enableTerminal){
        this.logTerminal(s)
      } else {
        Runtime.outputChannel.append(s)
      }
      
      if (typeof data === 'function') data(s)
    })

    proc.on('error', (e) => {
      this.notifyError(
        'Execution failed. Make sure ' + this.command + ' is installed in and your PATH',
      )
      if (typeof error === 'function') error(e.message)
    })

    proc.on('close', (code, signal) => {
      if (typeof close === 'function') close(code, signal)
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

  private static logTerminal(message: any) {
    var emitterText = message.replaceAll('\n', '\r\n')

    const enableTerminalColors = vscode.workspace.getConfiguration().get('pickle.terminal.enableCustomColors') as boolean

    if(enableTerminalColors){
      /*
      to configure : https://code.visualstudio.com/api/references/theme-color#integrated-terminal-colors    
      in settings.json in vscode

      "workbench.colorCustomizations": {
        "terminal.ansiBlue" : "#2472c8"
      }
      
      this unfortunately affects all terminals
      */

      //https://talyian.github.io/ansicolors/
      emitterText = emitterText.replaceAll('INFO', '\x1b[47mINFO\x1b[0m') //terminal.ansiBrightWhite
      emitterText = emitterText.replaceAll('WARN', '\x1b[43mWARN\x1b[0m') //terminal.ansiBrightYellow
      emitterText = emitterText.replaceAll('ERROR', '\x1b[41mERROR\x1b[0m') //terminal.ansiBrightRed
    }
   
    
    Runtime.terminalEmitter.fire(emitterText)
    Runtime.terminalEmitter.fire('\x1b[0m\r\n')
  }
}
