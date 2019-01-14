import * as vscode from 'vscode';
import * as path from 'path';
import { BaseNode, BaseNodeProvider } from './base';
import { Runtime } from './runtime';

export class TestNodeProvider extends BaseNodeProvider {
    private matchRegExp: RegExp = /^test "([\w\s\-]+)".*/;
    
    constructor() {
        super();
        vscode.commands.registerCommand('pickleExplorer.runTest', (test: TestNode) => Runtime.runTest(test.label, test.resourceUri.fsPath, test.args))
        vscode.commands.registerCommand('pickleExplorer.openTest', (test: TestNode) => test.openFile())
    }

    getExtension(): string { return "\.test" }

    getChildren(element?: TestNode): Thenable<TestNode[]> {
        if (!element) {
            return vscode.workspace.findFiles(this.basePath + '/**/*' + this.getExtension(), this.excludePath)
                .then(files => files.sort(this.sortFiles).map(this.getTestFileNode))
        }

        return vscode.workspace.openTextDocument(element.resourceUri)
            .then(doc => this.collectFromFile<TestNode>(doc, this.matchRegExp, (match, line) => new TestNode(doc.uri, match[1], line)))
    }

    getTestFileNode(uri: vscode.Uri): TestFileNode {
        return new TestFileNode(null, uri);
    }

    sortFiles(a: vscode.Uri, b: vscode.Uri): number {
        if (a < b) return -1;
        if (a > b) return 1;
        return 0;
    }
}

export class TestNode extends BaseNode {
    get args(): string[] { 
        return ['-test="' + this.label + '"'];
    };

    contextValue = 'pickle-runnable'
}

export class TestFileNode extends TestNode {
    constructor(label: string, uri?: vscode.Uri) {
        super(uri, label);
        this.collapsibleState = vscode.TreeItemCollapsibleState.Expanded;
    }

    get args(): string[] {
        return [];
    }

    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'test.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'test.svg')
    }

    contextValue = 'pickle-runnable'
}
