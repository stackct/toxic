import * as vscode from 'vscode';
import * as path from 'path';
import * as cp from 'child_process';

export class TestNodeProvider implements vscode.TreeDataProvider<TestNode> {
    private data: TestNode[];
    private outputChannel: vscode.OutputChannel;

    constructor(private workspaceRoot?: string) {
        this.loadData();
        this.outputChannel = vscode.window.createOutputChannel('Pickle Test Runner')

        vscode.commands.registerCommand('pickleExplorer.runTest', (test: TestNode) => this.runTest(test.label, test.resourceUri.fsPath))
        vscode.commands.registerCommand('pickleExplorer.openTest', (test?: TestNode) => this.openTest(test))
    }

    getTreeItem(element: TestNode): vscode.TreeItem {
        return element;
    }

    getChildren(element?: TestNode): Thenable<TestNode[]> {
        if (!element) {
            return Promise.resolve(this.data);
        }

        return vscode.workspace.openTextDocument(element.resourceUri)
            .then(doc => this.findTestsInFile(doc))
    }

    private loadData() {
        this.data = [] as TestNode[];
        vscode.workspace.findFiles('**/*.test')
            .then(files => this.data = files.map((uri) => new FileTestNode(null, uri)))
    }

    private openTest(test?: TestNode) {
        vscode.window.showTextDocument(test.resourceUri, { selection: test.selection } as vscode.TextDocumentShowOptions);
    }

    private findTestsInFile(doc: vscode.TextDocument): TestNode[] {
        let tests = [];
        let lines = doc.lineCount;

        for (let i = 0; i < lines; i++) {
            let line = doc.lineAt(i);
            let match = line.text.match(/^test "([\w\s]+)".*/)

            if (match != null && match.length > 0) {
                tests.push(new TestNode(doc.uri, match[1], i));
            }
        }

        return tests;
    }

    private runTest(name: string, path: string) {
        this.outputChannel.clear();
        this.outputChannel.show(true);

        let results = { success: 0, fail: 0 }

        let proc = cp.spawn('/home/aalonso/git/toxic/bin/toxic', ['-doDir=' + path]);
        proc.stdout.addListener("data", (chunk) => {
            let s = chunk.toString()
            this.outputChannel.append(s)

            let match = s.match(/^.*\sAgent Shutdown; durationMS=(\d+); tasks=(\d+); success=(\d+); fail=(\d+).*/);
            if (match != null && match.length > 0) {
                results.success = <any>match[3] as number;
                results.fail = <any>match[4] as number;
            }
        });

        proc.on("close", (code, signal) => {
            vscode.window.showInformationMessage('Test run completed; success:' + results.success + ', failed:' + results.fail, 'Dismiss', 'Rerun')
                .then(choice => {
                    if (choice == 'Rerun') this.runTest(name, path)
                })
        });
    }
}

export class TestNode extends vscode.TreeItem {
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

    iconPath = null;
    contextValue = 'test';
}

export class FileTestNode extends TestNode {
    constructor(label: string, uri?: vscode.Uri) {
        super(uri, label);
        this.collapsibleState = vscode.TreeItemCollapsibleState.Expanded;
    }

    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'test.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'test.svg')
    }

    contextValue = 'testFile'
}

