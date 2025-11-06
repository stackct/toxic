clean:
	@gradle clean
	@rm -fr gen build .gradle

compile:
	@gradle build -x test

build:
	@gradle build

test: build
	@bin/toxic -doDir=toxic/tests

buildWatch:
	@gradle build --continuous

testWatch: build
	@gradle test --continuous

.PHONY: build

.DEFAULT_GOAL := build
