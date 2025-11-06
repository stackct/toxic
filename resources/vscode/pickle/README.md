# Pickle Extension for Visual Studio Code

This extension provides code snippets, content navigation, and test execution support for Pickle.

## Requirements

- npm
- To run tests, you must have `toxic` installed and in your `PATH`.

## Installation (VS Code)

```bash
$ make
```

## Installation (Cursor)

```bash
$ make install-cursor
```


NOTE: If you are running on Windows, you are on your own.

## Features

- Code snippets
- Code navigation for Deps, Functions, and Tests
- Run tests (single, file, or all)

## Configuration Options

```
{
  // Enable notifications for failed tests
  "pickle.notifications.failure": true,

  // Enable notifications for successful tests
  "pickle.notifications.success": true,

  // List of runtime arguments in the format: -key=value
  "pickle.runtimeArgs": []
}
```

## Screencasts

### Navigation

![Pickle Navigation](./resources/pickle-explorer-navigation.gif)

### Authoring

![Pickle Authoring](./resources/pickle-explorer-authoring.gif)

## Next

- Run tests based on tags (include/exclude)
- Show available functions provided by external dependencies
- Figure out how to unit/integration test the extension
