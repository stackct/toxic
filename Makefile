-include ./common/build/ant/Makefile

update:
	@git submodule init
	@git pull --recurse-submodules
	@git submodule foreach 'git checkout master; git pull --rebase'

.DEFAULT_GOAL := update
