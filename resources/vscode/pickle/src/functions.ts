import * as vscode from 'vscode';
import * as path from 'path';

import { BaseNode, BaseNodeProvider } from './base';

export class FunctionNodeProvider extends BaseNodeProvider {
    
    private rex: RegExp = /^function "([\w\s]+)".*/;

    getTreeItem(element: FunctionNode): vscode.TreeItem {
        return element;
    }

    getChildren(element?: FunctionNode): Thenable<FunctionNode[]> {
        return vscode.workspace.findFiles('**/*.fn', 'gen/')
            .then(files => files.map(f => vscode.workspace.openTextDocument(f.fsPath)))
            .then(pdocs => Promise.all(pdocs))
            .then(docs => docs.map(doc => this.collectFromFile<FunctionNode>(doc, this.rex, (match, line) => new FunctionNode(doc.uri, match[1], line))))
            .then(anodes => this.flatten<FunctionNode>(anodes))
            .then(nodes => nodes.sort((a, b) => a.label < b.label ? -1 : 1))
    }
}

export class FunctionNode extends BaseNode {
    iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'light', 'function.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'dark', 'function.svg')
    }
}

