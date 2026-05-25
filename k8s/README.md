# Finans Portali — Kubernetes Manifests

Kustomize-based deployment for the four core services:

```
postgres   (StatefulSet, PVC, headless Service)
keycloak   (Deployment, Service)
backend    (Deployment, Service, ConfigMap, env-from Secret)
frontend   (Deployment, Service, nginx ConfigMap)
```

Two overlays live under `overlays/`:

- `dev/` — single-replica, NodePort Services, smaller resource requests. Drops
  onto minikube / kind / Docker Desktop without an ingress controller.
- `prod/` — multi-replica with pod anti-affinity, HPA on the backend, TLS-ready
  Ingress with cert-manager annotations, real image tags.

Side services from `docker-compose.yml` (Kafka, OpenSearch, Prometheus, Grafana,
Jaeger, otel-collector, fluent-bit, ldap, mailpit, playwright-service,
log-consumer) are deliberately out of scope for this first pass — they'll
land as a follow-up once the MVP path is proven.

---

## Prerequisites

- Managed Kubernetes cluster (EKS / GKE / AKS) with kubectl + kustomize
  available locally. `kubectl version --client` should print >= 1.27.
- A container registry the cluster can pull from (Docker Hub, ECR, GCR, GHCR…).
  The base manifests use `<registry>/finans-backend` and `<registry>/finans-frontend`
  as placeholders — the overlays patch in real image references.
- An ingress controller installed in the cluster (nginx by default; the
  prod overlay also assumes `cert-manager` for TLS).
- `metrics-server` running in-cluster if you want the HPA in the prod overlay
  to do anything (built-in on EKS/GKE/AKS).

---

## Build + push images

```bash
# Backend
docker build -t <registry>/finans-backend:v1.0.0 ./backend
docker push <registry>/finans-backend:v1.0.0

# Frontend
docker build -t <registry>/finans-frontend:v1.0.0 ./frontend
docker push <registry>/finans-frontend:v1.0.0
```

Update the `images:` block in the relevant overlay's `kustomization.yaml`
with the tag you just pushed.

---

## Provision secrets out-of-band

Every Secret is shipped as a `*.example.yaml` template that lives outside the
kustomization. Real secrets are NEVER committed — for each one:

```bash
cp k8s/base/postgres/secret.example.yaml /tmp/postgres-secret.yaml
# edit /tmp/postgres-secret.yaml — fill POSTGRES_PASSWORD, etc.
kubectl apply -n finans-portali -f /tmp/postgres-secret.yaml
rm /tmp/postgres-secret.yaml
```

Repeat for `keycloak-secret` and `backend-secret`.

For production deployments use a real secret-management story:

- **External Secrets Operator** — secrets live in AWS Secrets Manager / GCP
  Secret Manager / Vault, ESO syncs them as Kubernetes Secrets.
- **Sealed Secrets** — encrypt the Secret manifest with the cluster's public
  key and commit the sealed version. Bitnami's controller decrypts at
  apply-time.
- **SOPS + kustomize-sops** — encrypt manifests with age/PGP keys and decrypt
  at kustomize build time.

---

## Deploy

### Dev (local cluster)

```bash
# Render the manifests once to sanity-check before applying.
kubectl kustomize k8s/overlays/dev

# Apply.
kubectl apply -k k8s/overlays/dev

# Watch the rollout.
kubectl -n finans-portali-dev get pods -w
```

Once everything is `Running`, reach the services:

```bash
# Frontend — NodePort
minikube service frontend -n finans-portali-dev
# or
kubectl -n finans-portali-dev port-forward svc/frontend 5173:80

# Keycloak admin console
kubectl -n finans-portali-dev port-forward svc/keycloak 8090:8080
# → http://localhost:8090/admin
```

### Production (EKS / GKE / AKS)

```bash
# Update overlays/prod/kustomization.yaml:
#   - Replace `ghcr.io/yigitsheker/...` with your registry path
#   - Replace `app.example.com` + `auth.example.com` with real domains
#   - Confirm the cluster-issuer name matches your cert-manager setup

kubectl apply -k k8s/overlays/prod

# Watch the rollout + check HPA picks up real CPU readings (give it ~30s).
kubectl -n finans-portali rollout status deployment/backend
kubectl -n finans-portali get hpa
```

DNS — point `app.example.com` and `auth.example.com` at the ingress
controller's LoadBalancer IP / hostname. cert-manager will issue Let's Encrypt
certs automatically once DNS resolves.

---

## Keycloak realm bootstrap

The docker-compose stack has a `keycloak-bootstrap` sidecar that creates the
`finans` realm + `finans-backend-admin` confidential client on first start.
The Kubernetes manifests don't include that yet — for now, after Keycloak is
up, either:

1. Run the bootstrap script manually from a pod with `kubectl exec`:
   ```bash
   kubectl -n finans-portali exec -it deploy/keycloak -- /opt/keycloak/bin/kc.sh ...
   ```
2. Or use Keycloak's admin UI at `https://auth.example.com/admin` to import
   the realm export from `scripts/keycloak/finans-realm-export.json`.

A proper Job-based bootstrap is on the follow-up list.

---

## Cleanup

```bash
kubectl delete -k k8s/overlays/dev   # or overlays/prod
# Postgres PVC sticks around (good — it's data). Delete explicitly if you want a wipe:
kubectl -n finans-portali-dev delete pvc -l app.kubernetes.io/name=postgres
```

---

## Future work

- Job-based Keycloak realm bootstrap (mirror the `keycloak-bootstrap` container)
- Observability stack: Prometheus + Grafana + otel-collector
- Side services: Kafka, OpenSearch + Dashboards, playwright-service, log-consumer
- NetworkPolicies (default-deny + explicit allows)
- PodDisruptionBudgets on backend + frontend
- BackupClusterSchedule for the Postgres PVC (Velero or cloud snapshots)
