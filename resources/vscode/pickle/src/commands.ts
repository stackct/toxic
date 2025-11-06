import { BaseNode } from './base-node'
import { Runtime } from './runtime'

import { Range, Selection, TextEditorRevealType, Uri, commands, window, workspace } from 'vscode'

async function runAll() {
  Runtime.runTest('all')
}

async function runTest(name: string, path: string) {
  Runtime.runTest(name, path, [`-test="${name}"`])
}

async function runFileTests(path: string) {
  Runtime.runTest('', path)
}

async function openFile(node: BaseNode) {
  node.openFile()
}

async function goToFunction(name: string) {
  let searchPattern: string

  const parts = name.split('.')

  if (parts.length === 2) {
    searchPattern = `gen/deps/${parts[0]}/functions/**/*.fn`
    name = parts[1]
  } else {
    searchPattern = 'toxic/functions/**/*.fn'
  }

  const files = await workspace.findFiles(searchPattern)

  for (const file of files) {
    const document = await workspace.openTextDocument(file)
    const fileContents = document.getText()
    const pattern = `function "${name}"`

    if (fileContents.includes(pattern)) {
      const position = document.positionAt(fileContents.indexOf(pattern))
      const lineNumber = position.line
      const editor = await window.showTextDocument(document)

      editor.selection = new Selection(lineNumber, 0, lineNumber, 0)
      editor.revealRange(new Range(position, position), TextEditorRevealType.InCenter)
    }
  }
}

function goToLibrary(path: string) {
  if (!workspace.workspaceFolders || workspace.workspaceFolders.length == 0) {
    window.showErrorMessage('No workspace available')
    return
  }

  commands.executeCommand('workbench.view.explorer')
  commands.executeCommand('revealInExplorer', Uri.file(path), {
    focus: true,
    select: true,
  })
}

export { runTest, runFileTests, runAll, openFile, goToFunction, goToLibrary }
