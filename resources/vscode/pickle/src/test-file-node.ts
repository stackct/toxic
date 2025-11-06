import * as vscode from 'vscode'
import * as path from 'path'

import { TestNode } from './test-node'

export class TestFileNode extends TestNode {
  constructor(label: string, uri?: vscode.Uri) {
    super(uri, label)

    let expanded = vscode.workspace.getConfiguration().get('pickle.expand.tests') as boolean
    this.collapsibleState = expanded
      ? vscode.TreeItemCollapsibleState.Expanded
      : vscode.TreeItemCollapsibleState.Collapsed
  }

  get args(): string[] {
    return []
  }

  iconPath = {
    light: path.join(__filename, '..', '..', 'resources', 'test.svg'),
    dark: path.join(__filename, '..', '..', 'resources', 'test.svg'),
  }
}
