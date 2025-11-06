import { CodeLensProvider, TextDocument, CodeLens, Range, Position, workspace } from 'vscode'
import * as path from 'path'

class GoToFunctionCodeLensProvider implements CodeLensProvider {
  private regex: RegExp = /step "([\w\s\-.,()\/']+)".*/

  async provideCodeLenses(document: TextDocument): Promise<CodeLens[]> {
    if (!['.fn', '.test'].includes(path.extname(document.fileName))) {
      return []
    }

    const text = document.getText()
    const lines = text.split('\n')
    const codeLenses = []

    lines.forEach((line, n) => {
      let matches = line.match(this.regex)
      if (matches) {
        const functionName = matches[1]
        const p = new Position(n, line.indexOf(matches[0]))
        const r = document.getWordRangeAtPosition(p)
        const codeLens = new CodeLens(r, {
          title: '[go to definition]',
          tooltip: `Navigate to ${functionName}`,
          command: 'pickle.goToFunction',
          arguments: [functionName],
        })

        codeLenses.push(codeLens)
      }
    })

    return codeLenses
  }
}

class RunTestCodeLensProvider implements CodeLensProvider {
  private regex: RegExp = /^test "([\w\s\-,()\/']+)".*/

  async provideCodeLenses(document: TextDocument): Promise<CodeLens[]> {
    if (!['.test'].includes(path.extname(document.fileName))) {
      return []
    }

    const text = document.getText()
    const lines = text.split('\n')
    const codeLenses = []

    lines.forEach((line, n) => {
      let matches = line.match(this.regex)
      if (matches) {
        const testName = matches[1]
        const p = new Position(n, 0)
        const r = document.getWordRangeAtPosition(p)
        const codeLens = new CodeLens(r, {
          title: '[run test]',
          tooltip: `Run "${testName}"`,
          command: 'pickle.runTest',
          arguments: [testName, document.uri.path],
        })

        codeLenses.push(codeLens)
      }
    })

    return codeLenses
  }
}

class RunTestFileCodeLensProvider implements CodeLensProvider {
  async provideCodeLenses(document: TextDocument): Promise<CodeLens[]> {
    if (!['.test'].includes(path.extname(document.fileName))) {
      return []
    }

    return [
      new CodeLens(new Range(0, 0, 0, 0), {
        title: '[run file tests]',
        tooltip: 'Run all tests in this file',
        command: 'pickle.runFileTests',
        arguments: [document.uri.path],
      }),
    ]
  }
}

class GoToLibraryCodeLensProvider implements CodeLensProvider {
  private regex: RegExp = /path\s+"(.*)"/

  async provideCodeLenses(document: TextDocument): Promise<CodeLens[]> {
    if (!['.fn'].includes(path.extname(document.fileName))) {
      return []
    }

    const text = document.getText()
    const lines = text.split('\n')
    const codeLenses = []

    const parent = path.dirname(path.dirname(document.uri.fsPath))
    const libraryPath = path.join(parent, 'library')

    lines.forEach((line, n) => {
      let matches = line.match(this.regex)
      if (matches) {
        const libpath = matches[1].replace(/[{]{2}[\s]*libPath[\s]*[}]{2}/, libraryPath)
        const p = new Position(n, line.indexOf(matches[0]))
        const r = document.getWordRangeAtPosition(p)
        const codeLens = new CodeLens(r, {
          title: '[go to library]',
          tooltip: `Navigate to ${libpath}`,
          command: 'pickle.goToLibrary',
          arguments: [libpath],
        })

        codeLenses.push(codeLens)
      }
    })

    return codeLenses
  }
}

export {
  GoToFunctionCodeLensProvider,
  RunTestCodeLensProvider,
  RunTestFileCodeLensProvider,
  GoToLibraryCodeLensProvider,
}
