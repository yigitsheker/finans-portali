# GKE Deployment — Google Cloud + GitHub Actions CD

Bu rehber, **Finans Portalı** çekirdek uygulamasını (PostgreSQL + Keycloak +
Backend + Frontend) **Google Kubernetes Engine (GKE)** üzerine kurar ve
`main`'e her push'ta otomatik deploy eden **Continuous Deployment** hattını
ayağa kaldırır.

- **Auth:** Workload Identity Federation (anahtarsız — repo'da JSON key yok)
- **Image kayıt:** Artifact Registry
- **Veritabanı:** in-cluster Postgres StatefulSet (PersistentVolume)
- **Giriş:** GCE Ingress (L7 HTTP(S) LB) + global statik IP + Google-managed TLS
- **Tetik:** `main` push → CI yeşil → build & push → `kubectl apply -k`

Manifest'ler: [`k8s/overlays/gke/`](overlays/gke/) · İş akışı:
[`.github/workflows/cd.yml`](../.github/workflows/cd.yml)

---

## 0. Ön koşullar

- Faturalandırması açık bir GCP projesi
- Yerelde `gcloud` CLI (kurulum komutlarını çalıştırmak için)
- GitHub repo'da admin yetkisi (variables + secrets eklemek için)
- İki alan adı (örn. `app.example.com`, `auth.example.com`) ve DNS üzerinde
  A-kaydı oluşturma yetkisi

Aşağıdaki değerleri kendi ortamına göre belirle ve kabuğa export et:

```bash
export PROJECT_ID="finans-portali-prod"        # GCP proje kimliği
export REGION="europe-west1"                    # Artifact Registry + cluster bölgesi
export CLUSTER="finans-portali"                 # GKE cluster adı
export REPO="finans-portali"                    # Artifact Registry repo adı
export GH_REPO="yigitsheker/finans-portali"     # owner/repo
export APP_DOMAIN="app.example.com"
export AUTH_DOMAIN="auth.example.com"

gcloud config set project "$PROJECT_ID"
```

---

## 1. API'leri etkinleştir

```bash
gcloud services enable \
  container.googleapis.com \
  artifactregistry.googleapis.com \
  iamcredentials.googleapis.com \
  compute.googleapis.com
```

---

## 2. Artifact Registry deposu

```bash
gcloud artifacts repositories create "$REPO" \
  --repository-format=docker \
  --location="$REGION" \
  --description="Finans Portali container images"
```

İmajlar şu yola gider:
`REGION-docker.pkg.dev/PROJECT_ID/REPO/finans-backend` ve `.../finans-frontend`.

---

## 3. GKE cluster

Maliyet/işletim kolaylığı için **Autopilot** öneriyoruz (node yönetimi yok):

```bash
gcloud container clusters create-auto "$CLUSTER" \
  --location="$REGION"
```

> Standart cluster istersen: `gcloud container clusters create "$CLUSTER"
> --location="$REGION" --num-nodes=2 --machine-type=e2-standard-2`. Standart'ta
> `metrics-server` HPA için hazır gelir; Autopilot'ta da yerleşiktir.

---

## 4. Global statik IP + DNS

```bash
gcloud compute addresses create finans-portali-ip --global
gcloud compute addresses describe finans-portali-ip --global --format='value(address)'
```

Dönen IP için DNS'te **iki A-kaydı** oluştur:

| Host | Tip | Değer |
|------|-----|-------|
| `app.example.com`  | A | (yukarıdaki IP) |
| `auth.example.com` | A | (yukarıdaki IP) |

> Google-managed sertifika, DNS A-kayıtları IP'yi gösterene kadar `Provisioning`
> durumunda kalır; yayıldıktan sonra ~15-60 dk içinde `Active` olur.

---

## 5. Workload Identity Federation (keyless auth)

GitHub Actions'ın GCP'ye anahtarsız kimlik doğrulaması için bir WIF havuzu +
deployer service account oluştur:

```bash
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')

# 5a. WIF havuzu
gcloud iam workload-identity-pools create github-pool \
  --location=global --display-name="GitHub Actions Pool"

# 5b. OIDC provider — sadece bu repo'dan gelen token'lara güven
gcloud iam workload-identity-pools providers create-oidc github-provider \
  --location=global --workload-identity-pool=github-pool \
  --display-name="GitHub provider" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository=='${GH_REPO}'" \
  --issuer-uri="https://token.actions.githubusercontent.com"

# 5c. Deployer service account
gcloud iam service-accounts create finans-deployer \
  --display-name="Finans Portali CD deployer"
DEPLOYER_SA="finans-deployer@${PROJECT_ID}.iam.gserviceaccount.com"

# 5d. Gerekli roller: image push + cluster deploy
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${DEPLOYER_SA}" --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:${DEPLOYER_SA}" --role="roles/container.developer"

# 5e. GitHub repo'nun WIF kimliğine SA'yı impersonate etme izni
gcloud iam service-accounts add-iam-policy-binding "$DEPLOYER_SA" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/attribute.repository/${GH_REPO}"

# 5f. Workflow'a yazılacak provider kaynak adı
echo "GCP_WIF_PROVIDER=projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
echo "GCP_DEPLOYER_SA=${DEPLOYER_SA}"
```

---

## 6. GitHub Actions değişkenleri ve secret'ları

Repo → **Settings → Secrets and variables → Actions**.

**Variables** sekmesi:

| İsim | Örnek değer |
|------|-------------|
| `GCP_PROJECT_ID` | `finans-portali-prod` |
| `GCP_REGION` | `europe-west1` |
| `GKE_CLUSTER` | `finans-portali` |
| `GKE_LOCATION` | `europe-west1` |
| `ARTIFACT_REPO` | `finans-portali` |
| `APP_DOMAIN` | `app.example.com` |
| `AUTH_DOMAIN` | `auth.example.com` |

**Secrets** sekmesi:

| İsim | Değer |
|------|-------|
| `GCP_WIF_PROVIDER` | 5f çıktısındaki `projects/.../providers/github-provider` |
| `GCP_DEPLOYER_SA` | `finans-deployer@PROJECT.iam.gserviceaccount.com` |

`gh` CLI ile:

```bash
gh variable set GCP_PROJECT_ID --body "$PROJECT_ID" --repo "$GH_REPO"
gh variable set GCP_REGION     --body "$REGION"     --repo "$GH_REPO"
gh variable set GKE_CLUSTER    --body "$CLUSTER"    --repo "$GH_REPO"
gh variable set GKE_LOCATION   --body "$REGION"     --repo "$GH_REPO"
gh variable set ARTIFACT_REPO  --body "$REPO"       --repo "$GH_REPO"
gh variable set APP_DOMAIN     --body "$APP_DOMAIN"  --repo "$GH_REPO"
gh variable set AUTH_DOMAIN    --body "$AUTH_DOMAIN" --repo "$GH_REPO"

gh secret set GCP_WIF_PROVIDER --body "projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/github-pool/providers/github-provider" --repo "$GH_REPO"
gh secret set GCP_DEPLOYER_SA  --body "$DEPLOYER_SA" --repo "$GH_REPO"
```

---

## 7. Secret'ları cluster'a uygula (bir kez, elle)

Uygulama sırları **git'e girmez**; cluster'a out-of-band uygulanır.
Şablonları kopyala, gerçek değerleri doldur, uygula:

```bash
# Cluster kimlik bilgisi
gcloud container clusters get-credentials "$CLUSTER" --location="$REGION"

# Namespace (overlay zaten oluşturur ama secret'ları önce koymak için)
kubectl create namespace finans-portali --dry-run=client -o yaml | kubectl apply -f -

# Şablonlardan kopyala
cp k8s/base/postgres/secret.example.yaml /tmp/postgres-secret.yaml
cp k8s/base/keycloak/secret.example.yaml /tmp/keycloak-secret.yaml
cp k8s/base/backend/secret.example.yaml  /tmp/backend-secret.yaml
# → /tmp/*.yaml içindeki "change-me" ve boş değerleri gerçek değerlerle doldur

kubectl apply -n finans-portali -f /tmp/postgres-secret.yaml
kubectl apply -n finans-portali -f /tmp/keycloak-secret.yaml
kubectl apply -n finans-portali -f /tmp/backend-secret.yaml
rm /tmp/postgres-secret.yaml /tmp/keycloak-secret.yaml /tmp/backend-secret.yaml
```

> Postgres'in `keycloak_db` veritabanını da oluşturması gerekir (Keycloak ayrı
> şemada). Bu `k8s/base/postgres/init-configmap.yaml` ile ilk açılışta yapılır.

---

## 8. İlk deploy

İki yol var:

**A) Otomatik (önerilen):** `main`'e push at. CI yeşil olunca `cd.yml` devreye
girer, imajları build+push eder ve overlay'i uygular.

**B) Elle (ilk doğrulama için):**

```bash
# İmajları build + push
gcloud auth configure-docker "${REGION}-docker.pkg.dev" --quiet
IMG="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO}"
docker build -t "$IMG/finans-backend:manual"  ./backend  && docker push "$IMG/finans-backend:manual"
docker build -t "$IMG/finans-frontend:manual" ./frontend && docker push "$IMG/finans-frontend:manual"

# Overlay'deki PLACEHOLDER'ları doldur (workflow bunu sed ile otomatik yapar)
cd k8s/overlays/gke
kustomize edit set image \
  "<registry>/finans-backend=$IMG/finans-backend:manual" \
  "<registry>/finans-frontend=$IMG/finans-frontend:manual"
sed -i \
  -e "s|PLACEHOLDER_LOCATION-docker.pkg.dev/PLACEHOLDER_PROJECT|${REGION}-docker.pkg.dev/${PROJECT_ID}|g" \
  -e "s|PLACEHOLDER_APP_DOMAIN|${APP_DOMAIN}|g" \
  -e "s|PLACEHOLDER_AUTH_DOMAIN|${AUTH_DOMAIN}|g" \
  kustomization.yaml managed-certificate.yaml
cd ../../..

kubectl apply -k k8s/overlays/gke
```

> **Not:** Elle `sed` çalıştırdıysan overlay dosyalarını **commit'leme** —
> PLACEHOLDER'lar repoda kalmalı ki CD workflow her seferinde taze doldursun.
> `git checkout -- k8s/overlays/gke` ile geri al.

---

## 9. Doğrulama

```bash
kubectl -n finans-portali get pods
kubectl -n finans-portali rollout status deployment/backend
kubectl -n finans-portali get ingress finans-portali
kubectl -n finans-portali describe managedcertificate finans-portali-cert
```

- Ingress `ADDRESS` sütunu statik IP'yi göstermeli.
- ManagedCertificate `Status: Active` olunca HTTPS hazırdır (DNS yayılımına
  bağlı, ilk seferde ~15-60 dk).
- `https://app.example.com` → uygulama, `https://auth.example.com` → Keycloak.

---

## 10. Mimari özet

```
İnternet
   │  (DNS A → global static IP)
   ▼
GCE Ingress (L7 HTTPS LB) ── ManagedCertificate (otomatik TLS)
   │                          FrontendConfig (HTTP→HTTPS 301)
   ├── app.example.com  → Service/frontend (NodePort)  → nginx pods (2)
   └── auth.example.com → Service/keycloak (NodePort)  → keycloak pod (1)
                                   │
   Service/backend (NodePort, BackendConfig health=/actuator/health/readiness)
                                   │  ← nginx /api/* proxy + frontend çağrıları
                          backend pods (2, HPA 3-10 @ %70 CPU)
                                   │
                          Service/postgres (ClusterIP) → Postgres StatefulSet (PVC)
```

Her servisin LB sağlık kontrolü `BackendConfig` ile gerçek health path'ine
bağlanır (bkz. [`backendconfig.yaml`](overlays/gke/backendconfig.yaml)).

---

## 11. Sık karşılaşılanlar

| Belirti | Neden / Çözüm |
|---------|----------------|
| Sertifika `Provisioning`'de takılı | DNS A-kayıtları statik IP'yi göstermiyor. `dig app.example.com` ile doğrula. |
| Ingress backend `UNHEALTHY` | BackendConfig health path 200 dönmüyor. Pod ready mi? `kubectl describe ingress`. |
| Keycloak token `iss` hatası | `KC_HOSTNAME` ve backend `ISSUER_URI` `https://AUTH_DOMAIN` ile birebir aynı olmalı (overlay bunu set eder). |
| Backend Postgres'e bağlanamıyor | `postgres-secret` uygulanmamış veya `keycloak_db` yok. §7'yi tekrar et. |
| CD `wait-for-ci` zaman aşımı | CI job adı `"Backend (Maven build)"` ile eşleşmeli (`ci.yml`). |
