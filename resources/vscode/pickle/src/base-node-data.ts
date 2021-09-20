import * as vscode from 'vscode'
import { BaseNode } from './base-node'

export class BaseNodeData {
  public items: BaseNode[]

  constructor(uri?: vscode.Uri) {
    this.items = [] as BaseNode[]
  }
}
