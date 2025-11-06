import * as vscode from 'vscode'

import { BaseNodeProvider } from './base-node-provider'
import { Runtime } from './runtime'
import { TestNode } from './test-node'
import { TestFileNode } from './test-file-node'

export class TestNodeProvider extends BaseNodeProvider {
  private matchRegExp: RegExp = /^test "([\w\s\-,()\/']+)".*/

  constructor() {
    super()
  }

  getExtension(): string {
    return '.test'
  }

  getChildren(element?: TestNode): Thenable<TestNode[]> {
    if (!element) {
      return vscode.workspace
        .findFiles(this.basePath + '/**/*' + this.getExtension(), this.excludePath)
        .then((files) => files.sort(this.sortFiles).map(this.getTestFileNode))
    }

    return vscode.workspace
      .openTextDocument(element.resourceUri)
      .then((doc) =>
        this.collectFromFile<TestNode>(
          doc,
          this.matchRegExp,
          (match, line) => new TestNode(doc.uri, match[1], line),
        ),
      )
  }

  getTestFileNode(uri: vscode.Uri): TestFileNode {
    return new TestFileNode(null, uri)
  }

  sortFiles(a: vscode.Uri, b: vscode.Uri): number {
    let a1 = a.path.substring(a.path.lastIndexOf('/') + 1)
    let b1 = b.path.substring(b.path.lastIndexOf('/') + 1)

    if (a1 < b1) return -1
    if (a1 > b1) return 1
    return 0
  }
}
