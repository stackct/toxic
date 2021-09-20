import * as vscode from 'vscode'
import { BaseNode } from './base-node'

export abstract class BaseNodeProvider implements vscode.TreeDataProvider<BaseNode> {
  private fsWatcher: vscode.FileSystemWatcher
  private _onDidChangeTreeData: vscode.EventEmitter<any> = new vscode.EventEmitter<any>()
  readonly onDidChangeTreeData: vscode.Event<any> = this._onDidChangeTreeData.event

  constructor() {
    this.fsWatcher = vscode.workspace.createFileSystemWatcher(
      vscode.workspace.rootPath + '/toxic/**/*' + this.getExtension(),
    )
    this.fsWatcher.onDidCreate((uri) => this.refresh(uri))
    this.fsWatcher.onDidDelete((uri) => this.refresh(uri))
    this.fsWatcher.onDidChange((uri) => this.refresh(uri))
  }

  abstract getChildren(element?: BaseNode): Thenable<BaseNode[]>
  abstract getExtension(): string

  basePath = 'toxic'

  excludePath = 'gen/'

  getTreeItem(element: BaseNode) {
    return element
  }

  refresh(uri: vscode.Uri) {
    // TODO: This refreshes the entire tree. The scope of the refresh could be reduced to just the node
    // that changed, but will require a separate data structure to map Uri -> BaseNode.
    this._onDidChangeTreeData.fire()
  }

  get args(): string[] {
    return []
  }

  collectFromFile<BaseNode>(
    doc: vscode.TextDocument,
    rex: RegExp,
    matchFn: (match: RegExpMatchArray, line: number) => BaseNode,
  ): BaseNode[] {
    let entries = []
    let lines = doc.lineCount

    for (let i = 0; i < lines; i++) {
      let line = doc.lineAt(i)
      let match = line.text.match(rex)

      if (match != null && match.length > 0) {
        entries.push(matchFn(match, i))
      }
    }

    return entries.sort(this.sortNodes)
  }

  sortNodes(a: BaseNode, b: BaseNode): number {
    console.log('sorting node', a, b)

    if (a.label < b.label) return -1
    if (a.label > b.label) return 1
    return 0
  }

  flatten<BaseNode>(nodes: BaseNode[][]): BaseNode[] {
    return [].concat(...nodes) as BaseNode[]
  }
}
