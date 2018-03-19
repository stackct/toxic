-include ./common/build/ant/Makefile

update:
	@git submodule update --recursive --remote --init
	@git submodule foreach 'git checkout master; git pull --rebase'

.DEFAULT_GOAL := update
