# GKE — Kapatma (maliyet durdurma) ve Tekrar Açma

GKE cluster + global statik IP + Load Balancer **saatlik ücret** yazar. Demo'yu
kullanmadığın sürede kapat, ihtiyaç olunca tek seferde geri kur. Tüm manifest'ler
ve CD repoda kalır; silinen sadece çalışan kaynaklardır.

> PowerShell. Değişkenleri her yeni pencerede tekrar tanımla:
> ```powershell
> $PROJECT_ID = "finans-portali-498018"
> $REGION     = "europe-west1"
> $CLUSTER    = "finans-portali"
> gcloud config set project $PROJECT_ID
> ```

---

## A) Maliyeti durdur

### Seçenek 1 — Cluster'ı sil (önerilen, en çok tasarruf)

GKE Autopilot'ta esas ücret cluster + node'lar. Sil:

```powershell
gcloud container clusters delete $CLUSTER --location=$REGION --quiet
```

> Statik IP'yi **tutmak** istersen (tekrar açınca aynı IP → aynı nip.io adresi →
> Keycloak redirect URI'lerini değiştirmene gerek kalmaz) burada bırak. Statik
> IP'nin ayrılmış-ama-kullanılmayan ücreti çok küçüktür (~aylık birkaç ₺).
> Tamamen sıfır maliyet istersen IP'yi de sil:
> ```powershell
> gcloud compute addresses delete finans-portali-ip --global --quiet
> ```
> (IP'yi silersen yeni açılışta farklı IP gelir → nip.io adresi değişir →
> realm redirect URI'lerini + GitHub APP_DOMAIN/AUTH_DOMAIN değişkenlerini
> yeni IP'ye göre güncellemen gerekir. O yüzden IP'yi tutmak daha pratik.)

Artifact Registry'deki image'lar ve cluster içi PostgreSQL verisi (PVC) cluster
ile birlikte silinir. Image'lar CD ile yeniden üretilir; DB verisi (portföy,
alarmlar) **kaybolur** — demo için sorun değil, ilk açılışta seed + Yahoo'dan
yeniden dolar.

### Seçenek 2 — Cluster'ı tut, sadece ölçeği sıfırla (daha hızlı geri dönüş, ama cluster ücreti devam eder)

Autopilot'ta pod'ları 0'a indirmek node ücretini azaltır ama Autopilot
yönetim/altyapı ücreti sürer; **tam tasarruf için Seçenek 1'i kullan.** Yine de
hızlı duraklatma istersen:

```powershell
gcloud container clusters get-credentials $CLUSTER --location=$REGION
kubectl -n finans-portali scale deployment --all --replicas=0
kubectl -n finans-portali scale statefulset postgres --replicas=0
```

---

## B) Tekrar Aç

### 1. (Cluster sildiysen) cluster'ı yeniden oluştur

```powershell
gcloud container clusters create-auto $CLUSTER --location=$REGION
gcloud container clusters get-credentials $CLUSTER --location=$REGION
```

> İlk kurulumdaki tek-seferlik şeyler (API'ler, Artifact Registry, Workload
> Identity Federation, GitHub variables/secrets) **kalıcıdır, tekrar gerekmez.**
> Statik IP'yi sildiysen §B.1a, tuttuysan atla.

#### 1a. (Sadece statik IP'yi de sildiysen) yeniden oluştur + adresleri güncelle
```powershell
gcloud compute addresses create finans-portali-ip --global
gcloud compute addresses describe finans-portali-ip --global --format="value(address)"
```
Yeni IP geldiyse `<yeni-ip>` ile şunları güncelle (eski IP `34.120.195.216` yerine):
- GitHub repo variables: `APP_DOMAIN=app.<yeni-ip>.nip.io`, `AUTH_DOMAIN=auth.<yeni-ip>.nip.io`
- `k8s/base/keycloak/finans-realm.json` içindeki redirect/webOrigin/post-logout URI'leri
- (Bu değişiklikleri commit'le ki CD doğru değerleri kullansın.)

### 2. GKE node SA'sına Artifact Registry okuma izni ver (yeni cluster'da gerekebilir)

```powershell
$PROJECT_NUMBER = gcloud projects describe $PROJECT_ID --format="value(projectNumber)"
gcloud projects add-iam-policy-binding $PROJECT_ID `
  --member="serviceAccount:$PROJECT_NUMBER-compute@developer.gserviceaccount.com" `
  --role="roles/artifactregistry.reader"
```

### 3. Uygulama secret'larını yeniden uygula (cluster sildiyse DB ile birlikte gittiler)

> **KISA YOL (önerilen):** Repo kökündeki `apply-secrets.ps1` + `.secrets.local.env`
> ile tek komut. Bir kez kurulum:
> ```powershell
> Copy-Item .secrets.local.env.example .secrets.local.env   # bir kez
> notepad .secrets.local.env                                 # gerçek değerleri doldur (bir kez)
> ```
> Sonra HER açılışta sadece:
> ```powershell
> .\apply-secrets.ps1
> ```
> `.secrets.local.env` gitignore'da (asla commit edilmez). Script KC_DB_PASSWORD =
> POSTGRES_PASSWORD eşleşmesini de otomatik doğrular. Aşağıdaki elle yöntem alternatiftir.

#### Elle yöntem (script kullanmıyorsan)

```powershell
kubectl create namespace finans-portali --dry-run=client -o yaml | kubectl apply -f -

# Şablonlardan kopyala, gerçek değerleri doldur (bkz. GKE_DEPLOYMENT.md §7):
Copy-Item k8s/base/postgres/secret.example.yaml $env:TEMP\postgres-secret.yaml
Copy-Item k8s/base/keycloak/secret.example.yaml $env:TEMP\keycloak-secret.yaml
Copy-Item k8s/base/backend/secret.example.yaml  $env:TEMP\backend-secret.yaml
# → $env:TEMP\*.yaml dosyalarındaki değerleri doldur:
#   - postgres POSTGRES_PASSWORD = güçlü şifre
#   - keycloak KC_DB_PASSWORD = postgres POSTGRES_PASSWORD ile AYNI
#   - keycloak KEYCLOAK_ADMIN_PASSWORD, KC_BACKEND_CLIENT_SECRET
#   - backend SPRING_MAIL_USERNAME/PASSWORD (Gmail app password) + API keys
notepad $env:TEMP\postgres-secret.yaml ; notepad $env:TEMP\keycloak-secret.yaml ; notepad $env:TEMP\backend-secret.yaml

kubectl apply -n finans-portali -f $env:TEMP\postgres-secret.yaml
kubectl apply -n finans-portali -f $env:TEMP\keycloak-secret.yaml
kubectl apply -n finans-portali -f $env:TEMP\backend-secret.yaml
Remove-Item $env:TEMP\postgres-secret.yaml, $env:TEMP\keycloak-secret.yaml, $env:TEMP\backend-secret.yaml
```

### 4. Deploy et

**Otomatik (önerilen):** GitHub'dan CD'yi çalıştır → her şeyi build edip uygular:
Actions → "CD — Deploy to GKE" → Run workflow → main → Run.

**Veya** boş bir commit / küçük değişiklik push'la (CI yeşil → CD tetiklenir).

> Cluster'ı sadece scale-0 ile durdurduysan (Seçenek 2), geri açmak için
> deploy'a gerek yok — sadece ölçeği geri ver:
> ```powershell
> kubectl -n finans-portali scale statefulset postgres --replicas=1
> kubectl -n finans-portali scale deployment --all --replicas=1
> ```

### 5. Doğrula

```powershell
kubectl -n finans-portali get pods
kubectl -n finans-portali get ingress finans-portali        # ADDRESS = statik IP
kubectl -n finans-portali describe managedcertificate finans-portali-cert  # Status: Active
```

- Sertifika `Active` olunca (yeni cluster'da ~15-60 dk) `https://app.<IP>.nip.io` açılır.
- Keycloak realm yeni DB'ye ilk açılışta import edilir (loginTheme=finance-theme,
  nip.io redirect/logout URI'leri realm JSON'da hazır — artık kcadm ile elle
  düzeltmeye gerek yok).

---

## Özet

| Durum | Komut |
|------|-------|
| **Kapat (tam tasarruf)** | `gcloud container clusters delete $CLUSTER --location=$REGION --quiet` |
| **Aç** | cluster create-auto → AR izni → secret'lar → CD Run workflow |
| **Hızlı duraklat/sürdür** | `kubectl scale ... --replicas=0` / `--replicas=1` |

Detaylı ilk-kurulum (WIF, API'ler, GitHub secrets) için: [GKE_DEPLOYMENT.md](GKE_DEPLOYMENT.md).
