import * as vscode from 'vscode'

export abstract class BaseNode extends vscode.TreeItem {
  constructor(
    public uri: vscode.Uri,
    public label?: string,
    public line?: number,
    public parent?: BaseNode,
  ) {
    super(uri)
    this.label = label
  }

  iconPath = null

  contextValue = 'pickle-item'

  tooltip = this.label

  command = {
    title: 'Locate',
    command: 'pickleExplorer.openFile',
    arguments: [this],
    tooltip: 'Locate',
  } as vscode.Command

  selection = {
    start: new vscode.Position(this.line, 0),
    end: new vscode.Position(this.line, 0),
  } as vscode.Selection

  public openFile() {
    vscode.window.showTextDocument(this.resourceUri, {
      selection: this.selection,
    })
  }
}
