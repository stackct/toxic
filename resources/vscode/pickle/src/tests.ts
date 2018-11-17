import * as vscode from 'vscode';
import * as path from 'path';
import { BaseNode, BaseNodeProvider } from './base';
import { Runtime } from './runtime';

export class TestNodeProvider extends BaseNodeProvider {
    private matchRegExp: RegExp = /^test "([\w\s]+)".*/;
    
    private data: TestNode[];

    constructor() {
        super();
        this.loadData();
        vscode.commands.registerCommand('pickleExplorer.runTest', (test: TestNode) => Runtime.runTest(test.label, test.resourceUri.fsPath))
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
            .then(doc => this.collectFromFile<TestNode>(doc, this.matchRegExp, (match, line) => new TestNode(doc.uri, match[1], line)))
    }

    private loadData() {
        this.data = [] as TestNode[];

        vscode.workspace.findFiles(this.basePath + '/**/*.test', 'gen/')
            .then(files => this.data = files.map((uri) => new TestFileNode(null, uri)))
    }
}

export class TestNode extends BaseNode {
    contextValue = 'pickle-runnable'
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

