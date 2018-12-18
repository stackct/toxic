-include ./common/build/ant/Makefile

VSCODE_DIR:=$(PROJECT_DIR)/resources/vscode/pickle

vscode:
	@$(VSCODE_DIR)/install.sh

update:
	@git submodule update --recursive --remote --init
	@git submodule foreach 'git checkout master; git pull --rebase'

run:
	$(PROJECT_DIR)/bin/toxic-ui

rund:
	$(PROJECT_DIR)/bin/toxic-ui-debug

purge:
	@rm -rf $(PROJECT_DIR)/jobs

.DEFAULT_GOAL := update
