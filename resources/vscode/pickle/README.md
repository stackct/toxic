# Pickle Extension for Visual Studio COde

This extension provides code snippets, content navigation, and test execution support for Pickle.

## Requirements

* npm
* To run tests, you must have `toxic` installed and in your `PATH`.

## Installation

Build the extension from source:

```
npm install
npm run compile
```

Enable this extension by creating a symlink from directory containing this document into your Visual Studio Code extensions directory:

```
ln -s <THIS_DIR> $HOME/.vscode/extensions/pickle
```

NOTE: If you are running on Windows, you are on your own.

## Next

* Run all Pickle tests
* Run all Pickle tests in a file
* Run tests based on tags (include/exclude)
* Refresh explorer views when files change
* Show available functions provided by external dependencies
* Figure out how to unit/integration test the extension
