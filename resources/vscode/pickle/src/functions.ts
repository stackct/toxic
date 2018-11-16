import * as vscode from 'vscode';
import * as path from 'path';

export class FunctionNodeProvider implements vscode.TreeDataProvider<FunctionNode> {
    
    private data: FunctionNode[];

    constructor(private workspaceRoot?: string) {
        this.loadData();

        vscode.commands.registerCommand('pickleExplorer.openFunction', (fn?: FunctionNode) => this.openFunction(fn))
    }

    getTreeItem(element: FunctionNode): vscode.TreeItem {
        return element;
    }

    getChildren(element?: FunctionNode): Thenable<FunctionNode[]> {
        return vscode.workspace.findFiles('**/*.fn', 'gen/')
            .then(files => files.map(f => vscode.workspace.openTextDocument(f.fsPath)))
            .then(docs => Promise.all(docs))
            .then(docs => [].concat(...docs.map(doc => this.findFunctionsInFile(doc))) as FunctionNode[])
            .then(fns => fns.sort((a,b) => a.label < b.label ? -1 : 1))
    }

    private loadData() {
        this.data = [] as FunctionNode[];
        
    }

    private openFunction(fn?: FunctionNode) {
        vscode.window.showTextDocument(fn.resourceUri, { selection: fn.selection } as vscode.TextDocumentShowOptions);
    }

    private findFunctionsInFile(doc: vscode.TextDocument): FunctionNode[] {
        let tests = [];
        let lines = doc.lineCount;

        for (let i = 0; i < lines; i++) {
            let line = doc.lineAt(i);
            let match = line.text.match(/^function "([\w\s]+)".*/)

            if (match != null && match.length > 0) {
                tests.push(new FunctionNode(doc.uri, match[1], i));
            }
        }

        return tests;
    }
}

export class FunctionNode extends vscode.TreeItem {
    constructor(private uri: vscode.Uri, label?: string, private line?: number) {
        super(uri);
        this.label = label;
    }

    get tooltip(): string {
        return `${this.label}`;
    }

    get selection(): vscode.Selection {
        return { start: new vscode.Position(this.line, 0), end: new vscode.Position(this.line, 0) } as vscode.Selection;
    }

    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'light', 'function.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'dark', 'function.svg')
    }

    contextValue = 'function';
}

