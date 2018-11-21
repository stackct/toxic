import * as vscode from 'vscode';

export abstract class BaseNodeProvider implements vscode.TreeDataProvider<BaseNode> {
    private fsWatcher: vscode.FileSystemWatcher;
    private _onDidChangeTreeData: vscode.EventEmitter<any> = new vscode.EventEmitter<any>();
    readonly onDidChangeTreeData: vscode.Event<any> = this._onDidChangeTreeData.event;

    constructor() {
        this.fsWatcher = vscode.workspace.createFileSystemWatcher(vscode.workspace.rootPath + '/toxic/**/*' + this.getExtension());
        this.fsWatcher.onDidCreate(uri => this.refresh(uri));
        this.fsWatcher.onDidDelete(uri => this.refresh(uri));
        this.fsWatcher.onDidChange(uri => this.refresh(uri));
    }

    abstract getChildren(element?: BaseNode): Thenable<BaseNode[]>;
    abstract getExtension(): string;
    
    get basePath(): string {
        return 'toxic'
    }
    
    get excludePath(): string {
        return 'gen/';
    }
    
    getTreeItem(element: BaseNode) {
        return element;
    }
    
    refresh(uri: vscode.Uri) {
        // TODO: This refreshes the entire tree. The scope of the refresh could be reduced to just the node
        // that changed, but will require a separate data structure to map Uri -> BaseNode.
        this._onDidChangeTreeData.fire()
    }
    
    get args(): string[] { 
        return [];
    };
    
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
        public line?: number,
        public parent?: BaseNode
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

    public get command(): vscode.Command {
        return { title: "Locate", command: "pickleExplorer.openFile", arguments: [this], tooltip: "Locate" } as vscode.Command;
    }
}

export class BaseNodeData {
    public items: BaseNode[];

    constructor(uri?: vscode.Uri) {
        this.items = [] as BaseNode[];
    }
}
