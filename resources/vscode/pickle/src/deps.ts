import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';

export class Dep extends vscode.TreeItem {
    get tooltip(): string {
        return `${this.label}`;
    }

    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'light', 'dep.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'dark', 'dep.svg')
    }

    contextValue = 'dep';
}

export class DepNodeProvider implements vscode.TreeDataProvider<Dep> {

    constructor(private workspaceRoot: string) {
    }

    getTreeItem(element: Dep): vscode.TreeItem {
        return element;
    }

    getChildren(element?: Dep): Thenable<Dep[]> {
        return vscode.workspace.findFiles('**/*.dep')
            .then(files => files.map((uri) => vscode.workspace.openTextDocument(uri)))
            .then(fs => Promise.all(fs))
            .then(docs => Promise.resolve([].concat(...docs.map(doc => this.findDepsInFile(doc)))))
    }

    private findDepsInFile(doc: vscode.TextDocument): Dep[] {
        let deps = [];
        let lines = doc.lineCount;

        for (let i = 0; i < lines; i++) {
            let line = doc.lineAt(i);
            let match = line.text.match(/^dep "(\w+)".*/)

            if (match != null && match.length > 0) {
                deps.push(new Dep(match[1]));
            }
        }

        return deps;
    }
}