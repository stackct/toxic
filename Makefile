-include ./common/build/ant/Makefile

update:
	@git submodule update --recursive --remote --init
	@git pull --recurse-submodules
	@git submodule foreach 'git checkout master; git pull --rebase'

.DEFAULT_GOAL := update
