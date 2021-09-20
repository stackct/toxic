import * as vscode from 'vscode'
import * as path from 'path'
import { BaseNode } from './base-node'
import { BaseNodeProvider } from './base-node-provider'

export class DepNodeProvider extends BaseNodeProvider {
  private matchRegExp: RegExp = /^dep "([\w\s]+)".*/
  basePath: string

  getExtension(): string {
    return '.dep'
  }

  getChildren(element?: DepNode): Thenable<DepNode[]> {
    return vscode.workspace
      .findFiles(this.basePath + '/**/*' + this.getExtension(), this.excludePath)
      .then((files) => files.map((uri) => vscode.workspace.openTextDocument(uri)))
      .then((pdocs) => Promise.all(pdocs))
      .then((docs) =>
        docs.map((doc) =>
          this.collectFromFile<DepNode>(
            doc,
            this.matchRegExp,
            (match, line) => new DepNode(doc.uri, match[1], line),
          ),
        ),
      )
      .then((dnodes) => this.flatten<DepNode>(dnodes))
  }

  collectFromFile<BaseNode>(
    doc: vscode.TextDocument,
    rex: RegExp,
    matchFn: (match: RegExpMatchArray, line: number) => BaseNode,
  ): BaseNode[] {
    throw new Error('Method not implemented.')
  }

  flatten<T>(dnodes: any[]): any {
    throw new Error('Method not implemented.')
  }
}

export class DepNode extends BaseNode {
  iconPath = {
    light: path.join(__filename, '..', '..', 'resources', 'light', 'dep.svg'),
    dark: path.join(__filename, '..', '..', 'resources', 'dark', 'dep.svg'),
  }
}
