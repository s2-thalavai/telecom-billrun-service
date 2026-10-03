#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
IMAGE="${IMAGE:-telecom-billrun-service:1.0.0}"
CONTEXT="$(kubectl config current-context)"
docker image inspect "$IMAGE" >/dev/null 2>&1 || docker build -t "$IMAGE" .
case "$CONTEXT" in
  minikube*)      minikube image load "$IMAGE" ;;
  kind-*)         kind load docker-image "$IMAGE" --name "${CONTEXT#kind-}" ;;
  docker-desktop) echo "Docker Desktop shares the local image store." ;;
  *)              echo "Remote cluster: push $IMAGE and update k8s/kustomization.yaml (images:)." ;;
esac
kubectl apply -k k8s/
kubectl -n telecom-billrun rollout status statefulset/mysql --timeout=300s
kubectl -n telecom-billrun rollout status deployment/billrun-service --timeout=300s
kubectl -n telecom-billrun get pods,svc,ingress
