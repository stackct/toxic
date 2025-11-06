import * as vscode from 'vscode'
import { DepNode, DepNodeProvider } from './deps';
import { FunctionNodeProvider } from './functions';


class BaseDepCompletionProvider implements vscode.CompletionItemProvider {
    provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext): vscode.ProviderResult<vscode.CompletionItem[] | vscode.CompletionList<vscode.CompletionItem>> {
        throw new Error('Method not implemented');
    }

    getCurentEditorLine(document: vscode.TextDocument): string {
        const editor = vscode.window.activeTextEditor;
        const selection = editor.selection;
        const currentLineRange = editor.document.lineAt(selection.active.line).range;
        const line = document.getText(currentLineRange);

        return line
    }

    buildCodeCompletionItem(
        position: vscode.Position, 
        toxicFunction: string,
        namespace?: string,
        deleteTrigger?: boolean): vscode.CompletionItem {

        const insertionText = !namespace ? toxicFunction : namespace + '.' + toxicFunction

        let completionItem = new vscode.CompletionItem(insertionText, vscode.CompletionItemKind.Method )

        completionItem.insertText = new vscode.SnippetString(insertionText)

        if(deleteTrigger) {
            const rangeToRemove = new vscode.Range(position.line, position.character-1, position.line, position.character);
            completionItem.additionalTextEdits = [vscode.TextEdit.delete(rangeToRemove)];
        }

        return completionItem
    }
}

export class PickleLocalCompletionProvider extends BaseDepCompletionProvider {

    private matchRegExp: RegExp = /^.*step "([\.\w\s\-,()\/']+)",.*/

    provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext) {

        const enable = vscode.workspace.getConfiguration().get('pickle.codecompletion.enable') as boolean

        if(!enable) {
            return undefined
        }

        let functionsProvider = new FunctionNodeProvider()

        const line = this.getCurentEditorLine(document)
        let match = line.match(this.matchRegExp)

        if(line.trim().startsWith("step \"") && match != null && match[1] !== '.')
        {
            return undefined
        }

        return functionsProvider.getChildren()
            .then(items => items.map(i => {
                return this.buildCodeCompletionItem(position, i.label, null, true)
            }))
    }
}

export class PickleDepsCompletionProvider extends BaseDepCompletionProvider {


    private matchRegExp: RegExp = /^.*step "([\w\s\-,()\/']+)\.",.*/
    
    async provideCompletionItems(document: vscode.TextDocument, position: vscode.Position, token: vscode.CancellationToken, context: vscode.CompletionContext) {

        const enable = vscode.workspace.getConfiguration().get('pickle.codecompletion.enable') as boolean

        if(!enable) {
            return undefined
        }


        let functionsProvider = new DepNodeProvider()

        let deps = await functionsProvider.getChildren();
        let items = [] as vscode.CompletionItem[]

        const line = this.getCurentEditorLine(document)
        let match = line.match(this.matchRegExp)

        for(const externalDepNode of deps){
            let functionFiles = await functionsProvider.getChildren(externalDepNode)
            let namespace = externalDepNode.label
            let deleteTrigger = true

            if(match != null && match[1] !== namespace) {
                continue
            }

            if(match != null && match[1] == namespace) {
                namespace = null
                deleteTrigger = false
            }

            for(const fileNode of functionFiles){

                let functions = await functionsProvider.getChildren(fileNode)

                items = items.concat( functions.map(f => {
                    return this.buildCodeCompletionItem(position, f.label, namespace, deleteTrigger)
                }))
            }
        }

        return items
    }

}
