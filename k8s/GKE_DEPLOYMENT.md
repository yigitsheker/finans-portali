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

> **Kabuk:** Tüm komutlar **Windows PowerShell** içindir.
> - Satır devamı backtick (`` ` ``) iledir — bash'teki `\` PowerShell'de çalışmaz.
> - Değişkenler `$VAR = "deger"` ile tanımlanır (`export` yoktur).
> - `gcloud` / `gh` kuruluysa ve PATH'teyse aynen çalışır. gcloud kurulumu
>   PATH'i güncellemediyse §0'a bak.
> - Komutları **kopyalarken backtick'li blokları olduğu gibi** kopyala; tek
>   satıra indirgemek istersen backtick'leri ve satır sonlarını sil.

---

## 0. Ön koşullar

- Faturalandırması açık bir GCP projesi
- `gcloud` CLI (Google Cloud SDK) — kurulu değilse:
  ```powershell
  winget install --id Google.CloudSDK
  ```
  Kurulumdan sonra **yeni bir PowerShell penceresi aç**. `gcloud` hâlâ
  tanınmıyorsa PATH'e elle ekle (winget bazen eklemiyor):
  ```powershell
  [Environment]::SetEnvironmentVariable("Path", $env:Path + ";$env:LOCALAPPDATA\Google\Cloud SDK\google-cloud-sdk\bin", "User")
  ```
  Sonra pencereyi kapatıp yenisini aç ve doğrula:
  ```powershell
  gcloud version
  ```
- `kubectl` (Docker Desktop ile gelir) ve `kustomize` (gcloud bileşeni olarak
  kurulabilir):
  ```powershell
  gcloud components install kubectl gke-gcloud-auth-plugin kustomize
  ```
- GitHub repo'da admin yetkisi (variables + secrets eklemek için). İsteğe bağlı
  `gh` CLI: `winget install --id GitHub.cli`
- İki alan adı (örn. `app.example.com`, `auth.example.com`) ve DNS üzerinde
  A-kaydı oluşturma yetkisi

### Giriş + değişkenler

Önce Google hesabına giriş yap:

```powershell
gcloud auth login
```

Bu projenin **gerçek değerleri** aşağıda hazır. Pencereyi her kapattığında bu
bloğu tekrar çalıştır (değişkenler yalnızca açık oturumda yaşar):

```powershell
$PROJECT_ID  = "finans-portali-498018"      # GCP proje kimliği (gcloud projects list)
$REGION      = "europe-west1"               # Artifact Registry + cluster bölgesi
$CLUSTER     = "finans-portali"             # GKE cluster adı
$REPO        = "finans-portali"             # Artifact Registry repo adı
$GH_REPO     = "yigitsheker/finans-portali" # owner/repo
$STATIC_IP   = "34.120.195.216"             # §4'te oluşturulan global statik IP
# Alan adları — gerçek domain'in yoksa nip.io kullan (IP'ye otomatik çözülür):
$APP_DOMAIN  = "app.34.120.195.216.nip.io"
$AUTH_DOMAIN = "auth.34.120.195.216.nip.io"
# Gerçek domain'in varsa onları yaz, örn:
#   $APP_DOMAIN  = "app.seninsite.com"
#   $AUTH_DOMAIN = "auth.seninsite.com"

gcloud config set project $PROJECT_ID
```

> **nip.io notu:** `app.34.120.195.216.nip.io` gibi adresler ekstra DNS kaydı
> gerektirmeden doğrudan IP'ye çözülür — demo/test için idealdir. Ancak
> Google-managed TLS sertifikası `nip.io` için sağlanmaz; bu durumda uygulama
> HTTP üzerinden erişilir (HTTPS yerine). Tam HTTPS istiyorsan gerçek bir domain
> kullan ve §4'teki DNS A-kayıtlarını ekle.

> **Aldığın hata buydu:** `The required property [project] is not currently set`
> — yukarıdaki `gcloud config set project $PROJECT_ID` satırı bunu çözer. Proje
> ayarlanmadan hiçbir `gcloud` kaynak komutu çalışmaz.

---

## 1. API'leri etkinleştir

```powershell
gcloud services enable `
  container.googleapis.com `
  artifactregistry.googleapis.com `
  iamcredentials.googleapis.com `
  compute.googleapis.com
```

> Tek satır tercih edersen:
> ```powershell
> gcloud services enable container.googleapis.com artifactregistry.googleapis.com iamcredentials.googleapis.com compute.googleapis.com
> ```

---

## 2. Artifact Registry deposu

```powershell
gcloud artifacts repositories create $REPO `
  --repository-format=docker `
  --location=$REGION `
  --description="Finans Portali container images"
```

İmajlar şu yola gider:
`REGION-docker.pkg.dev/PROJECT_ID/REPO/finans-backend` ve `.../finans-frontend`.

---

## 3. GKE cluster

Maliyet/işletim kolaylığı için **Autopilot** öneriyoruz (node yönetimi yok):

```powershell
gcloud container clusters create-auto $CLUSTER `
  --location=$REGION
```

> Standart cluster istersen:
> ```powershell
> gcloud container clusters create $CLUSTER --location=$REGION --num-nodes=2 --machine-type=e2-standard-2
> ```
> `metrics-server` (HPA için gerekli) hem Autopilot hem Standard'da yerleşiktir.

---

## 4. Global statik IP + DNS

```powershell
gcloud compute addresses create finans-portali-ip --global
gcloud compute addresses describe finans-portali-ip --global --format="value(address)"
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
deployer service account oluştur.

```powershell
# Proje numarasını al (komut çıktısı doğrudan değişkene atanır)
$PROJECT_NUMBER = gcloud projects describe $PROJECT_ID --format="value(projectNumber)"

# 5a. WIF havuzu
gcloud iam workload-identity-pools create github-pool `
  --location=global --display-name="GitHub Actions Pool"

# 5b. OIDC provider — sadece bu repo'dan gelen token'lara güven
gcloud iam workload-identity-pools providers create-oidc github-provider `
  --location=global --workload-identity-pool=github-pool `
  --display-name="GitHub provider" `
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" `
  --attribute-condition="assertion.repository=='$GH_REPO'" `
  --issuer-uri="https://token.actions.githubusercontent.com"

# 5c. Deployer service account
gcloud iam service-accounts create finans-deployer `
  --display-name="Finans Portali CD deployer"
$DEPLOYER_SA = "finans-deployer@$PROJECT_ID.iam.gserviceaccount.com"

# 5d. Gerekli roller: image push + cluster deploy
gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$DEPLOYER_SA" --role="roles/container.developer"

# 5e. GitHub repo'nun WIF kimliğine SA'yı impersonate etme izni
$PRINCIPAL = "principalSet://iam.googleapis.com/projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/attribute.repository/$GH_REPO"
gcloud iam service-accounts add-iam-policy-binding $DEPLOYER_SA `
  --role="roles/iam.workloadIdentityUser" `
  --member=$PRINCIPAL

# 5f. Workflow'a yazılacak değerleri ekrana bas
$WIF_PROVIDER = "projects/$PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider"
Write-Output "GCP_WIF_PROVIDER=$WIF_PROVIDER"
Write-Output "GCP_DEPLOYER_SA=$DEPLOYER_SA"
```

> `--attribute-condition` içindeki `'$GH_REPO'` — çift tırnaklı string içinde
> tek tırnak gcloud'a literal aktarılır, `$GH_REPO` PowerShell tarafından
> genişletilir. Yani gcloud'a giden değer: `assertion.repository=='yigitsheker/finans-portali'`.

---

## 6. GitHub Actions değişkenleri ve secret'ları

Repo → **Settings → Secrets and variables → Actions**.

**Variables** sekmesi (bu projenin gerçek değerleri):

| İsim | Değer |
|------|-------|
| `GCP_PROJECT_ID` | `finans-portali-498018` |
| `GCP_REGION` | `europe-west1` |
| `GKE_CLUSTER` | `finans-portali` |
| `GKE_LOCATION` | `europe-west1` |
| `ARTIFACT_REPO` | `finans-portali` |
| `APP_DOMAIN` | `app.34.120.195.216.nip.io` (veya gerçek domain) |
| `AUTH_DOMAIN` | `auth.34.120.195.216.nip.io` (veya gerçek domain) |

**Secrets** sekmesi:

| İsim | Değer |
|------|-------|
| `GCP_WIF_PROVIDER` | 5f çıktısındaki tam değer: `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github-pool/providers/github-provider` |
| `GCP_DEPLOYER_SA` | `finans-deployer@finans-portali-498018.iam.gserviceaccount.com` |

> `PROJECT_NUMBER`, §5'te `$PROJECT_NUMBER = gcloud projects describe ...`
> komutuyla otomatik alınır; `GCP_WIF_PROVIDER`'ın içinde o sayı yer alır.
> Aşağıdaki `gh` komutları zaten §5'teki `$WIF_PROVIDER` ve `$DEPLOYER_SA`
> değişkenlerini kullandığı için elle yapıştırmana gerek yok.

`gh` CLI ile (PowerShell):

```powershell
gh variable set GCP_PROJECT_ID --body $PROJECT_ID  --repo $GH_REPO
gh variable set GCP_REGION     --body $REGION       --repo $GH_REPO
gh variable set GKE_CLUSTER    --body $CLUSTER      --repo $GH_REPO
gh variable set GKE_LOCATION   --body $REGION       --repo $GH_REPO
gh variable set ARTIFACT_REPO  --body $REPO         --repo $GH_REPO
gh variable set APP_DOMAIN     --body $APP_DOMAIN   --repo $GH_REPO
gh variable set AUTH_DOMAIN    --body $AUTH_DOMAIN  --repo $GH_REPO

gh secret set GCP_WIF_PROVIDER --body $WIF_PROVIDER --repo $GH_REPO
gh secret set GCP_DEPLOYER_SA  --body $DEPLOYER_SA  --repo $GH_REPO
```

> `$WIF_PROVIDER` ve `$DEPLOYER_SA` değişkenleri §5'te tanımlandı; aynı
> PowerShell oturumunda devam ediyorsan hazırdırlar.

---

## 7. Secret'ları cluster'a uygula (bir kez, elle)

Uygulama sırları **git'e girmez**; cluster'a out-of-band uygulanır.
Şablonları kopyala, gerçek değerleri doldur, uygula:

```powershell
# Cluster kimlik bilgisi
gcloud container clusters get-credentials $CLUSTER --location=$REGION

# Namespace (overlay zaten oluşturur ama secret'ları önce koymak için)
kubectl create namespace finans-portali --dry-run=client -o yaml | kubectl apply -f -

# Şablonlardan kopyala
Copy-Item k8s/base/postgres/secret.example.yaml $env:TEMP\postgres-secret.yaml
Copy-Item k8s/base/keycloak/secret.example.yaml $env:TEMP\keycloak-secret.yaml
Copy-Item k8s/base/backend/secret.example.yaml  $env:TEMP\backend-secret.yaml
# → $env:TEMP içindeki dosyalarda "change-me" ve boş değerleri gerçek değerlerle doldur
#   (örn:  notepad $env:TEMP\postgres-secret.yaml)

kubectl apply -n finans-portali -f $env:TEMP\postgres-secret.yaml
kubectl apply -n finans-portali -f $env:TEMP\keycloak-secret.yaml
kubectl apply -n finans-portali -f $env:TEMP\backend-secret.yaml

Remove-Item $env:TEMP\postgres-secret.yaml, $env:TEMP\keycloak-secret.yaml, $env:TEMP\backend-secret.yaml
```

> Postgres'in `keycloak_db` veritabanını da oluşturması gerekir (Keycloak ayrı
> şemada). Bu `k8s/base/postgres/init-configmap.yaml` ile ilk açılışta yapılır.

---

## 8. İlk deploy

İki yol var:

**A) Otomatik (önerilen):** `main`'e push at. CI yeşil olunca `cd.yml` devreye
girer, imajları build+push eder ve overlay'i uygular.

**B) Elle (ilk doğrulama için):**

```powershell
# İmajları build + push
gcloud auth configure-docker "$REGION-docker.pkg.dev" --quiet
$IMG = "$REGION-docker.pkg.dev/$PROJECT_ID/$REPO"

docker build -t "$IMG/finans-backend:manual"  ./backend
docker push "$IMG/finans-backend:manual"
docker build -t "$IMG/finans-frontend:manual" ./frontend
docker push "$IMG/finans-frontend:manual"

# Overlay'deki PLACEHOLDER'ları doldur (workflow bunu otomatik yapar)
Push-Location k8s/overlays/gke
kustomize edit set image `
  "<registry>/finans-backend=$IMG/finans-backend:manual" `
  "<registry>/finans-frontend=$IMG/finans-frontend:manual"

# PLACEHOLDER token'larını gerçek değerlerle değiştir
$repl = @{
  "PLACEHOLDER_LOCATION-docker.pkg.dev/PLACEHOLDER_PROJECT" = "$REGION-docker.pkg.dev/$PROJECT_ID"
  "PLACEHOLDER_APP_DOMAIN"  = $APP_DOMAIN
  "PLACEHOLDER_AUTH_DOMAIN" = $AUTH_DOMAIN
}
foreach ($f in "kustomization.yaml","managed-certificate.yaml") {
  $c = Get-Content $f -Raw
  foreach ($k in $repl.Keys) { $c = $c.Replace($k, $repl[$k]) }
  Set-Content $f $c -NoNewline
}
Pop-Location

kubectl apply -k k8s/overlays/gke
```

> **Not:** Elle değiştirme yaptıysan overlay dosyalarını **commit'leme** —
> PLACEHOLDER'lar repoda kalmalı ki CD workflow her seferinde taze doldursun.
> Geri al:
> ```powershell
> git checkout -- k8s/overlays/gke
> ```

---

## 9. Doğrulama

```powershell
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
| `gcloud` tanınmıyor | PATH eklenmemiş. §0'daki `SetEnvironmentVariable` satırını çalıştır, yeni pencere aç. |
| `property [project] is not currently set` | `gcloud config set project $PROJECT_ID` çalıştırılmamış (§0). |
| `\` ile yazılan komut hata veriyor | Bash sözdizimi. PowerShell'de satır devamı backtick (`` ` ``) ya da tek satır kullan. |
| Sertifika `Provisioning`'de takılı | DNS A-kayıtları statik IP'yi göstermiyor. `nslookup app.example.com` ile doğrula. nip.io kullanıyorsan bu otomatik çözülür; cert yine de ~15-60 dk sürebilir. |
| Pod `ImagePullBackOff` / `403 Forbidden` | GKE node SA'sının Artifact Registry okuma izni yok. `gcloud projects add-iam-policy-binding $PROJECT_ID --member="serviceAccount:$PROJECT_NUMBER-compute@developer.gserviceaccount.com" --role="roles/artifactregistry.reader"` (§5 sonrası bir kez). |
| Pod `InvalidImageName` (PLACEHOLDER...) | Overlay'i PLACEHOLDER'lar dolmadan `kubectl apply -k` ettin. Lokal apply için §8-B'deki PLACEHOLDER doldurma adımını çalıştır; normalde CD halleder. |
| Backend `CrashLoopBackOff` → "No resolvable bootstrap urls" | Log4j2 Kafka appender broker arıyor. Overlay `SPRING_PROFILES_ACTIVE=prod,nokafka` ile appender'ı kapatır (Kafka yoksa). |
| API her istek ~18 sn sürüyor | Aynı Kafka kökü: appender her log satırında broker'a yazmayı deneyip bekliyor. `nokafka` profili çözer (overlay'de var). |
| Backend pod sürekli restart (liveness) | İlk açılış 60 sn'yi aşıyor; base liveness erken vuruyor. Overlay'deki `startupProbe` (600 sn bütçe) bunu çözer. |
| Keycloak `502` / backend `UNHEALTHY` / endpoint boş | Keycloak `start-dev`'de 9000 health portu güvenilir değil → readiness probe başarısız → NEG boş. Overlay readiness/liveness'i 8080 `/realms/master`'a, BackendConfig health path'ini de `/realms/master`'a çeker. |
| Login'de URL `localhost:8090`'a gidiyor | Frontend Keycloak URL'i build-time `localhost` baked. Runtime-config çözer: `keycloak.js` `window.__RUNTIME_CONFIG__` okur, overlay `frontend-runtime-config` ConfigMap'i mount eder. **Kod değişikliği → yeni image build (CD) gerekir.** |
| Login'de "Invalid redirect_uri" | Realm `localhost`-only redirect URI ile import edilmiş. `finans-frontend` client'ına public URL ekle (kcadm `update clients/<id> -s redirectUris=[...] -s webOrigins=[...]`). |
| Mixed Content / Chrome hep https'e çeviriyor | nip.io+http demo'da Chrome HSTS/HTTPS-First zorlar. Kalıcı çözüm: tam HTTPS — managed-cert + FrontendConfig annotation'ları + issuer/KC_HOSTNAME/CORS `https://` (overlay'de bu yapı var). |
| BIST/hisse verisi gelmiyor | `market_quotes` taze DB'de boş; `PriceRefreshScheduler` startup'tan 60 sn sonra Yahoo'dan çeker (~2-3 dk). Kafka gecikmesi (yukarıda) bunu da yavaşlatıyordu — `nokafka` ile düzelir. Admin panelinden manuel refresh de tetiklenebilir. |
| HPA `FailedScaleUp` / GCE quota exceeded | Backend HPA çok yukarı ölçekledi. Overlay HPA'sı 1-3 aralığında (demo cluster quota'sına uygun). |
| CD otomatik tetiklenmiyor | CD `workflow_run` ile CI ("CI") tamamlanınca çalışır; CI başarılı bitmeli. Elle: Actions → CD → Run workflow (`workflow_dispatch`). |
