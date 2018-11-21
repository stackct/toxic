import * as vscode from 'vscode';
import * as path from 'path';
import * as cp from 'child_process';

export class Runtime {
    public static baseDir: string = path.join(vscode.workspace.rootPath, 'toxic', 'tests');
    private static command: string = 'toxic';
    private static outputChannel: vscode.OutputChannel;

    public static runTest(name: string, path: string = this.baseDir, args: string[] = []) {
        let results = { success: 0, fail: 0 }
        let postOptions = (choice: string) => {
            if (choice == 'Rerun') Runtime.runTest(name, path, args)
        }

        let onData = (data: string) => {
            let match = data.match(/^.*\sAgent Shutdown; durationMS=(\d+); tasks=(\d+); success=(\d+); fail=(\d+).*/);
            if (match != null && match.length > 0) {
                results.success = <any>match[3] as number;
                results.fail = <any>match[4] as number;
            }
        }

        let onError = (e: string) => {
            vscode.window.showErrorMessage('Something went wrong; reason=' + e);
        }

        let onClose = (code: number, signal: string) => {
            let message = 'Test run completed; success:' + results.success + ', failed:' + results.fail;
            let options = ['Dismiss', 'Rerun']
    
            if (code !== 0) {
                vscode.window.showErrorMessage(message, ...options).then(postOptions)
                return;
            }
            
            vscode.window.showInformationMessage(message, ...options).then(postOptions)
        }

        this.run(path, args, onData, onError, onClose);
    }

    private static run(path: string, args: string[], data?: (d: string) => any, error?: (e: string) => any, close?: (c: number, s: string) => any) {
        Runtime.outputChannel = Runtime.outputChannel || vscode.window.createOutputChannel('Pickle Runtime');
        Runtime.outputChannel.clear();
        Runtime.outputChannel.show(true);

        let configArgs = vscode.workspace.getConfiguration().get('pickle.runtimeArgs') as string[];

        let proc = cp.spawn(this.command, ['-doDir=' + path].concat(...args).concat(...configArgs));

        proc.stdout.addListener("data", (chunk) => {
            let s = chunk.toString()
            Runtime.outputChannel.append(s)
            
            if (data) data(s);
        });

        proc.on("error", (e) => {
            vscode.window.showErrorMessage('Execution failed. Make sure ' + this.command + ' is installed in and your PATH');
            if (error) error(e.message)
        });

        proc.on("close", (code, signal) => {
            if (close) close(code, signal);
        });
    }
}
