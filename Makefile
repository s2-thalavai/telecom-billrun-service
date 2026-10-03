# Telecom bill run service – common tasks. Run `make` or `make help`.
APP_NAME  := telecom-billrun-service
VERSION   := 1.0.0
IMAGE     := $(APP_NAME):$(VERSION)
MVN       ?= mvn
KUBECTL   ?= kubectl
BASE_URL  ?= http://localhost:8080
NAMESPACE := telecom-billrun

.DEFAULT_GOAL := help
.PHONY: help test it verify build run clean docker-build env-file up down nuke logs smoke metrics watch crash-demo \
	    k8s-deploy k8s-status k8s-port-forward k8s-delete

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

test: ## Unit, MockMvc slice and JPA tests (fast)
	$(MVN) -B test

it: ## Integration tests: real background jobs, cancellation, queue rejection, crash recovery (+ MySQL with Docker)
	$(MVN) -B verify -Dtest=SkipUnitTests -Dsurefire.failIfNoSpecifiedTests=false

verify: ## Everything + coverage report (target/site/jacoco/index.html)
	$(MVN) -B clean verify

build: ## Package the jar (skip tests)
	$(MVN) -B clean package -DskipTests

run: ## Run locally (H2, 5,000 demo accounts, console at /)
	./scripts/run-dev.sh

clean: ## Remove build output
	$(MVN) -B clean

docker-build: ## Build the Docker image
	docker build -t $(IMAGE) .

env-file:
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

up: env-file ## Start MySQL + app with Docker Compose
	docker compose up -d --build
	@echo "Console: $(BASE_URL)"

down: ## Stop Compose (keeps the DB volume)
	docker compose down

nuke: ## Stop Compose and delete the DB volume
	docker compose down -v

logs: ## Follow app logs ([job=...] on worker lines)
	docker compose logs -f app

smoke: ## 202 -> progress -> COMPLETED, idempotent rerun, cancellation
	BASE_URL=$(BASE_URL) ./scripts/smoke-test.sh

metrics: ## Job and executor metrics
	BASE_URL=$(BASE_URL) ./scripts/job-metrics.sh

watch: ## Follow a job's SSE progress: make watch JOB=<id>
	BASE_URL=$(BASE_URL) ./scripts/watch-job.sh $(JOB)

crash-demo: ## Kill the app mid-run (Compose) and watch the job resume from its checkpoint
	BASE_URL=$(BASE_URL) ./scripts/crash-demo.sh

k8s-deploy: ## Build, load into local cluster, apply k8s/
	./scripts/k8s-deploy.sh

k8s-status: ## Pods, services, ingress
	$(KUBECTL) -n $(NAMESPACE) get pods,svc,ingress

k8s-port-forward: ## Forward localhost:8080 to the service
	$(KUBECTL) -n $(NAMESPACE) port-forward svc/billrun-service 8080:80

k8s-delete: ## Delete all K8s resources (including DB data)
	./scripts/k8s-teardown.sh
