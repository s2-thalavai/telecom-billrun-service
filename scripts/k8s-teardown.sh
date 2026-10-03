#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl delete -k k8s/ --ignore-not-found
kubectl -n telecom-billrun delete pvc --all --ignore-not-found 2>/dev/null || true
kubectl delete namespace telecom-billrun --ignore-not-found
