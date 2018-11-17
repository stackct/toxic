import * as vscode from 'vscode';

export abstract class BaseNodeProvider implements vscode.TreeDataProvider<BaseNode> {
    abstract getTreeItem(element: BaseNode);
    abstract getChildren(element?: BaseNode): Thenable<BaseNode[]>;

    get basePath(): string {
        return 'toxic'
    }

    collectFromFile<BaseNode>(doc: vscode.TextDocument, rex: RegExp, matchFn: (match: RegExpMatchArray, line: number) => BaseNode): BaseNode[] {
        let entries = [];
        let lines = doc.lineCount;
    
        for (let i = 0; i < lines; i++) {
            let line = doc.lineAt(i);
            let match = line.text.match(rex)
    
            if (match != null && match.length > 0) {
                entries.push(matchFn(match, i))
            }
        }
    
        return entries;
    }

    flatten<BaseNode>(nodes: BaseNode[][]): BaseNode[] {
        return [].concat(...nodes) as BaseNode[];
    }
   
}

export abstract class BaseNode extends vscode.TreeItem {
    constructor(
        public uri: vscode.Uri, 
        public label?: string, 
        public line?: number
    ) {
        super(uri);
        this.label = label;
    }

    iconPath = null;
    contextValue = 'pickle-item';

    get tooltip(): string {
        return `${this.label}`;
    }

    get selection(): vscode.Selection {
        return { start: new vscode.Position(this.line, 0), end: new vscode.Position(this.line, 0) } as vscode.Selection;
    }

    public openFile() {
        vscode.window.showTextDocument(this.resourceUri, { selection: this.selection });
    }
}
