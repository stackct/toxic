import * as vscode from 'vscode'
import * as path from 'path'
import { BaseNode } from './base-node'
import { BaseNodeProvider } from './base-node-provider'

export class FunctionNodeProvider extends BaseNodeProvider {
  private matchRegExp: RegExp = /^function "([\w\s]+)".*/

  getExtension(): string {
    return '.fn'
  }

  getChildren(element?: FunctionNode): Thenable<FunctionNode[]> {
    return vscode.workspace
      .findFiles(this.basePath + '/**/*' + this.getExtension(), this.excludePath)
      .then((files) => files.map((f) => vscode.workspace.openTextDocument(f.fsPath)))
      .then((pdocs) => Promise.all(pdocs))
      .then((docs) =>
        docs.map((doc) =>
          this.collectFromFile<FunctionNode>(
            doc,
            this.matchRegExp,
            (match, line) => new FunctionNode(doc.uri, match[1], line),
          ),
        ),
      )
      .then((anodes) => this.flatten<FunctionNode>(anodes))
      .then((nodes) => nodes.sort((a, b) => (a.label < b.label ? -1 : 1)))
  }
}

export class FunctionNode extends BaseNode {
  iconPath = {
    light: path.join(__filename, '..', '..', 'resources', 'light', 'function.svg'),
    dark: path.join(__filename, '..', '..', 'resources', 'dark', 'function.svg'),
  }
}
