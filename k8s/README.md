# Finans Portali — Kubernetes

Local-first Kubernetes setup for Docker Desktop's built-in K8s. Four core
services:

```
postgres   (StatefulSet, PVC, headless Service)
keycloak   (Deployment, Service, realm import from finans-realm.json)
backend    (Deployment, Service, ConfigMap, env-from Secret)
frontend   (Deployment, Service, nginx ConfigMap)
```

Plus a one-shot **bootstrap Job** that patches the Keycloak realm + creates
the `finans-backend-admin` client (mirrors the `keycloak-bootstrap`
container from `docker-compose.yml`).

---

## Quick start (Docker Desktop)

### 1. Turn on Kubernetes + containerd image store

Docker Desktop → **Settings**:

1. **Kubernetes** → tick **"Enable Kubernetes"** → **Apply & restart**.
   Wait ~30 sec for the bottom-left status to show `Kubernetes running`.

2. **General** → tick **"Use containerd for pulling and storing images"**
   → **Apply & restart**. **This is required** — when off, Docker keeps
   images in the legacy moby store, separate from the K8s containerd
   store, and pods fail with `ErrImageNeverPull` on locally-built images.

Verify from PowerShell:
```powershell
kubectl config current-context   # should print "docker-desktop"
kubectl get nodes                # should list one node "Ready"
```

### 2. Run the deploy script

```powershell
.\k8s\deploy-local.ps1
```

That single command does the whole cycle:

1. Sanity-checks the cluster.
2. Builds `finans-backend:local` + `finans-frontend:local` from the
   repo's Dockerfiles.
3. Creates the `finans-portali-dev` namespace.
4. Applies the three example Secrets (defaults work for local dev).
5. `kubectl apply -k k8s/overlays/dev`.
6. Waits for Postgres → Keycloak → backend → frontend rollouts.
7. Starts port-forward background jobs for the three Services.

When it's done you'll see:

```
Frontend       → http://localhost:5173
Keycloak admin → http://localhost:8090/admin
Backend API    → http://localhost:8080/actuator/health
```

The Keycloak admin username/password live in `k8s/base/keycloak/secret.example.yaml`
(`admin` / `change-me-in-production` by default — fine for local).

### 3. Iterate

Made a code change?

```powershell
# Backend / frontend code changed → rebuild image + roll the deploy
.\k8s\deploy-local.ps1

# K8s manifest changed but no image rebuild needed → skip the docker build
.\k8s\deploy-local.ps1 -SkipBuild

# Start over with a clean slate (deletes pods + PVC):
.\k8s\deploy-local.ps1 -Clean
```

### 4. Tail logs / debug

```powershell
kubectl -n finans-portali-dev get pods                  # what's running
kubectl -n finans-portali-dev logs deploy/backend -f    # backend stream
kubectl -n finans-portali-dev describe pod <pod-name>   # event timeline
kubectl -n finans-portali-dev exec -it deploy/backend -- /bin/sh   # shell in
```

### 5. Stop port-forwards / tear down

```powershell
# Stop background port-forward jobs from the deploy script
Get-Job fp-* | Stop-Job
Get-Job fp-* | Remove-Job

# Full teardown (keeps Docker images cached for the next round)
kubectl delete -k k8s\overlays\dev

# Nuke the Postgres data too
kubectl -n finans-portali-dev delete pvc --all
```

---

## How the bits fit together

```
                       kustomize build
                              │
               ┌──────────────┴──────────────┐
               ▼                             ▼
       k8s/base (shared)              k8s/overlays/dev (local patches)
   ──────────────────────         ──────────────────────────────────
   Namespace                       namespace prefix
   Postgres StatefulSet            imagePullPolicy: Never
   Keycloak Deployment             local image tags
   Keycloak bootstrap Job          NodePort Services
   Backend Deployment              smaller resource requests
   Frontend Deployment
   Services for each
   Ingress (placeholder hosts)
   nginx ConfigMap
   Backend ConfigMap
   Realm-import ConfigMap (generated from finans-realm.json)
   Bootstrap-script ConfigMap (generated from keycloak-bootstrap.sh)
```

### Keycloak realm bootstrap

1. Keycloak starts with `--import-realm` and the JSON mounted at
   `/opt/keycloak/data/import/finans-realm.json` (via ConfigMap volume).
2. The `keycloak-bootstrap` Job runs once Keycloak is up — same
   kcadm.sh script the compose stack uses. It:
   - Patches realm settings (rememberMe, session lifespans, etc.)
   - Creates the `finans-backend-admin` confidential client
   - Maps `realm-management` roles to that client's service account

The bootstrap script + realm JSON live in `k8s/base/keycloak/` as direct
copies of `/scripts/keycloak-bootstrap.sh` and `/keycloak/finans-realm.json`.
Treat the source-tree versions as the source of truth — if you change one,
re-copy to the other (or run the manual sync inside `deploy-local.ps1`).

---

## Image-pulling gotchas

Docker Desktop's K8s shares the host's Docker daemon, so `docker build`
and `kubectl run` see the same image cache. That's why the overlay sets
`imagePullPolicy: Never` — without it, K8s would try to pull
`finans-backend:local` from a remote registry and fail with `ErrImagePull`.

If you ever see `ErrImagePull`/`ImagePullBackOff`:

1. Confirm the image exists: `docker images | findstr finans`
2. Confirm `kubectl get pod <pod> -o yaml | findstr imagePullPolicy`
   shows `Never`.

For **kind** or **minikube** (different K8s flavors that don't share
Docker daemon), you'd instead need:
```powershell
kind load docker-image finans-backend:local            # for kind
minikube image load finans-backend:local               # for minikube
```

---

## Secrets

`k8s/base/{postgres,keycloak,backend}/secret.example.yaml` ship with
default values that work fine for local dev. They're applied verbatim by
`deploy-local.ps1`.

If you want to keep real secrets out of the apply path (e.g. you don't
want the example FRED/EVDS keys in `backend-secret`), point each one at
a private copy:

```powershell
copy k8s\base\backend\secret.example.yaml C:\private\backend-secret.yaml
# edit C:\private\backend-secret.yaml — fill real APP_FRED_API_KEY, etc.
kubectl apply -n finans-portali-dev -f C:\private\backend-secret.yaml
# then re-run .\k8s\deploy-local.ps1 -SkipBuild
```

For production, External Secrets Operator or Sealed Secrets is the right
answer — but that's the future-prod path, not the local-dev path.

---

## Production overlay (reference only)

`k8s/overlays/prod/` exists as a starting point for if/when this lands on
EKS / GKE / AKS:

- 3-10 replica HPA on the backend (CPU-based)
- Pod anti-affinity for zone spread
- cert-manager TLS Ingress with real hostnames
- Pinned SemVer image tags (`v1.0.0`, not `latest`)

You don't need it for local. Ignore it until you have a cloud cluster.

---

## Future work

- Observability stack: Prometheus + Grafana + otel-collector
- Side services: Kafka, OpenSearch + Dashboards, playwright-service,
  log-consumer, mailpit, ldap
- NetworkPolicies (default-deny + explicit allows)
- PodDisruptionBudgets on backend + frontend
- Cloud-side: External Secrets Operator + Velero PVC backups
