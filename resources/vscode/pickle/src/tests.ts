import * as vscode from 'vscode';
import * as path from 'path';
import * as cp from 'child_process';
import { BaseNode, BaseNodeProvider } from './base';

export class TestNodeProvider extends BaseNodeProvider {
    private rex: RegExp = /^test "([\w\s]+)".*/;
    private data: TestNode[];
    private outputChannel: vscode.OutputChannel;

    constructor() {
        super();
        this.loadData();
        this.outputChannel = vscode.window.createOutputChannel('Pickle Test Runner')
        vscode.commands.registerCommand('pickleExplorer.runTest', (test: TestNode) => this.runTest(test.label, test.resourceUri.fsPath))
        vscode.commands.registerCommand('pickleExplorer.openTest', (test: TestNode) => test.openFile())
    }

    getTreeItem(element: TestNode): vscode.TreeItem {
        return element;
    }

    getChildren(element?: TestNode): Thenable<TestNode[]> {
        if (!element) {
            return Promise.resolve(this.data);
        }

        return vscode.workspace.openTextDocument(element.resourceUri)
            .then(doc => this.collectFromFile<TestNode>(doc, this.rex, (match, line) => new TestNode(doc.uri, match[1], line)))
    }

    private loadData() {
        this.data = [] as TestNode[];
        vscode.workspace.findFiles('**/*.test', 'gen/')
            .then(files => this.data = files.map((uri) => new TestFileNode(null, uri)))
    }

    private runTest(name: string, path: string) {
        this.outputChannel.clear();
        this.outputChannel.show(true);

        let results = { success: 0, fail: 0 }

        let proc = cp.spawn('toxic', ['-doDir=' + path]);
        proc.stdout.addListener("data", (chunk) => {
            let s = chunk.toString()
            this.outputChannel.append(s)

            let match = s.match(/^.*\sAgent Shutdown; durationMS=(\d+); tasks=(\d+); success=(\d+); fail=(\d+).*/);
            if (match != null && match.length > 0) {
                results.success = <any>match[3] as number;
                results.fail = <any>match[4] as number;
            }
        });

        let postOptions = (choice: string) => {
            if (choice == 'Rerun') this.runTest(name, path)
        }

        proc.on("error", (e) => vscode.window.showErrorMessage('Failed to run test. Make sure toxic is installed in and your PATH'));
        proc.on("close", (code, signal) => {
            let message = 'Test run completed; success:' + results.success + ', failed:' + results.fail;
            let options = ['Dismiss', 'Rerun']

            if (code !== 0) {
                vscode.window.showErrorMessage(message, ...options).then(postOptions)
                return;
            }
            
            vscode.window.showInformationMessage(message, ...options).then(postOptions)
        });
    }
}

export class TestNode extends BaseNode {
    contextValue = 'pickle-test'
}

export class TestFileNode extends TestNode {
    constructor(label: string, uri?: vscode.Uri) {
        super(uri, label);
        this.collapsibleState = vscode.TreeItemCollapsibleState.Expanded;
    }

    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'test.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'test.svg')
    }

    contextValue = 'pickle-item-container'
}

