-include ./common/build/ant/Makefile

VSCODE_DIR:=$(PROJECT_DIR)/resources/vscode/pickle

vscode:
	@$(VSCODE_DIR)/install.sh

update:
	@git submodule update --recursive --remote --init
	@git submodule foreach 'git checkout master; git pull --rebase'

.DEFAULT_GOAL := update
