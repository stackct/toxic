import * as vscode from 'vscode'
import * as path from 'path'
import { BaseNode } from './base-node'
import { BaseNodeProvider } from './base-node-provider'

export class DepNodeProvider extends BaseNodeProvider {
  private matchRegExp: RegExp = /^dep "([\w\s]+)".*/
  private matchFuncRegExp: RegExp = /^function "([\w\s]+)".*/
  basePath: string
  baseDepPath: string

  getExtension(): string {
    return '.dep'
  }

  getChildren(element?: DepNode): Thenable<DepNode[]> {
    if(!element) {
      return vscode.workspace
        .findFiles(this.basePath + '/**/*' + this.getExtension(), this.excludePath)
        .then((files) => files.map((uri) => vscode.workspace.openTextDocument(uri)))
        .then((pdocs) => Promise.all(pdocs))
        .then((docs) =>
          docs.map((doc) =>
            this.collectFromFile<DepNode>(
              doc,
              this.matchRegExp,
              (match, line) => new DepNode(match[1], doc.uri, DepType.Dep),
            ),
          ),
        )
        .then((dnodes) => this.flatten<DepNode>(dnodes))
    }

    if(element.nodeType == DepType.Dep) {
      return vscode.workspace
        .findFiles(this.baseDepPath + '/' + element.label + '/functions/**/*')
        .then((files) => files.map(file => new DepNode(null, file, DepType.File)))
    }

    if(element.nodeType == DepType.File) {
      return vscode.workspace
        .openTextDocument(element.resourceUri)
        .then((doc) =>
          this.collectFromFile<DepNode>(
            doc,
            this.matchFuncRegExp,
            (match, line) => new DepNode(match[1], doc.uri, DepType.Function, line),
          ),
        )
    }
  }
}

enum DepType {
  Dep,
  File,
  Function
}

export class DepNode extends BaseNode {
  constructor(label: string, uri?: vscode.Uri, type?: DepType, line?: number) {
    super(uri, label, line)

    let expanded = vscode.workspace.getConfiguration().get('pickle.expand.tests') as boolean
    this.nodeType = type

    if(this.nodeType != DepType.Function){

      this.collapsibleState = expanded
        ? vscode.TreeItemCollapsibleState.Expanded
        : vscode.TreeItemCollapsibleState.Collapsed
    }
    else{
      this.iconPath = {
        light: path.join(__filename, '..', '..', 'resources', 'light', 'function.svg'),
        dark: path.join(__filename, '..', '..', 'resources', 'dark', 'function.svg'),
      }
    }
  }

  nodeType = DepType.Dep

  iconPath = {
    light: path.join(__filename, '..', '..', 'resources', 'light', 'dep.svg'),
    dark: path.join(__filename, '..', '..', 'resources', 'dark', 'dep.svg'),
  }
}
