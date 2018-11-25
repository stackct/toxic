PROJECT_DIR:=$(abspath $(dir $(firstword $(MAKEFILE_LIST))))
VSCODE_DIR:=$(PROJECT_DIR)/resources/vscode/pickle

vscode:
	@$(VSCODE_DIR)/install.sh

.DEFAULT_GOAL := update
