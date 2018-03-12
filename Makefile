APP_NAME=toxic
ROOT_DIR=${CURDIR}
OUTPUT_DIR=${ROOT_DIR}/gen
APP_DIR=${ROOT_DIR}
BUILD_VERSION=$(shell cat VERSION)

clean:
	rm -fr ${OUTPUT_DIR}

docker: clean
	-docker rm -f ${APP_NAME}-extract 2>/dev/null
	mkdir -p ${OUTPUT_DIR}/${APP_NAME}-extract/publish
	docker build --no-cache -f ${APP_DIR}/Dockerfile.ant.build -t ${APP_NAME}:build ${APP_DIR}
	docker create --name ${APP_NAME}-extract ${APP_NAME}:build
	docker cp ${APP_NAME}-extract:/build ${OUTPUT_DIR}/${APP_NAME}-publish
	-docker rm -f ${APP_NAME}-extract 2>/dev/null
	docker build --no-cache --build-arg OUTPUT_DIR=gen/${APP_NAME}-publish -f ${APP_DIR}/Dockerfile -t ${APP_NAME}:latest .
	docker tag ${APP_NAME} ${APP_NAME}:${BUILD_VERSION}
	rm -fr extract

push: docker
	docker login -u ${DOCKER_USER} -p ${DOCKER_PASSWORD} ${DOCKER_REGISTRY}
	docker tag ${APP_NAME} ${DOCKER_REGISTRY}/${APP_NAME}
	docker push ${DOCKER_REGISTRY}/${APP_NAME}

package: docker

run:
	cd ${APP_DIR}; dotnet run

.PHONY: clean build publish package docker run test