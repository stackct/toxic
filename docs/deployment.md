# Helm Deployment

Charts are available for deployment via [Helm](https://github.com/kubernetes/helm).

## Local Deployment with Minikube

### Setup
```
minikube start
helm install --namespace toxic --name toxic helm/toxic
```

### Teardown
```
helm uninstall toxic
minikube delete
```

## Standard Deployment

NOTE: Non-local deployments will likely require custom values to be supplied as the default chart values (`helm/toxic/values.yaml)` are for local development.

```
helm install --namespace toxic --name toxic -f PATH_TO_VALUES helm/toxic
```
