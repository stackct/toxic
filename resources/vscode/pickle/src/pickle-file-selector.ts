import { DocumentFilter } from 'vscode'

export class PickleFileDocumentSelector implements DocumentFilter {
  public scheme: string = 'file'
  public pattern: string = '**/*.{test,fn}'
}
