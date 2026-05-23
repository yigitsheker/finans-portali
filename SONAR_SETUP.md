# SonarCloud kurulum adımları

Bu projede kod analizi için **SonarCloud** (SaaS, public repo için ücretsiz)
kullanılıyor. CI pipeline ve scan konfigürasyonu zaten commit'lendi:

- [`sonar-project.properties`](sonar-project.properties) — source path'leri,
  exclusion'lar, JaCoCo coverage path
- [`.github/workflows/ci.yml`](.github/workflows/ci.yml) — her `push to main`
  ve maintainer PR'ında SonarCloud scan'i çalıştırır

Aşağıdaki manuel adımlar **bir defa** yapılır:

## 1. SonarCloud hesabı + organizasyon

1. <https://sonarcloud.io/> → **"Log in with GitHub"** ile giriş
2. İlk girişte SonarCloud GitHub OAuth permission ister. **`yigitsheker`**
   hesabı için onay verin (sadece read-only access ister).
3. Sağ üstte **"+"** → **"Analyze new project"** seçeneğine tıklayın.
4. Açılan listede `yigitsheker` GitHub org'unu seçin.
   - Eğer "Choose an organization on GitHub" diye sorarsa, **"Create one"**
     diyerek `yigitsheker` adında bir SonarCloud organizasyonu açın
     (free plan, public repo'lar için ücretsiz). Bu işlem GitHub
     organizasyonu yaratmaz — SonarCloud kendi tarafında bir org yaratır.
5. **`yigitsheker/finans-portali`** repo'sunu seçin → **"Set up"**.

Bu adım SonarCloud tarafında otomatik olarak:
- `sonar.projectKey = yigitsheker_finans-portali`
- `sonar.organization = yigitsheker`

değerlerini set eder. Bu key'ler `sonar-project.properties` içinde zaten
aynı şekilde tanımlı — değiştirmenize gerek yok.

## 2. SONAR_TOKEN secret'ı

SonarCloud her PR/push'ta CI'dan analiz alabilmek için bir token gerektirir.

1. SonarCloud'da sağ üstte **profil ikonu → "My Account"**.
2. **"Security"** sekmesi.
3. **"Generate Token"** → name: `finans-portali-ci`, expires: **No expiration**
   (CI'da kullanılacak, manual rotation tercihinize göre).
4. Üretilen token'ı kopyalayın — bu sayfayı kapattıktan sonra bir daha
   göremezsiniz.

Şimdi token'ı GitHub repo secrets'a ekleyin:

1. GitHub'da repo sayfası → **Settings → Secrets and variables → Actions**.
2. **"New repository secret"**.
3. Name: `SONAR_TOKEN` (büyük-küçük harfler önemli)
4. Value: kopyaladığınız token.
5. Save.

## 3. Quality Gate (opsiyonel ama önerilir)

Default quality gate "Sonar way" — yeni kod için:
- Coverage ≥ 80%
- Duplicated lines ≤ 3%
- Bug / vulnerability sayısı = 0
- Security hotspots review edilmiş

Bu varsayılan zaten makul, değiştirmeye gerek yok. PR'larda kırmızı
yeşil otomatik gelir.

## 4. İlk scan

Yukarıdaki adımlar bittikten sonra `main`'e bir push (veya bu commit
zaten merge'lendiyse hiçbir şey yapmadan) CI'da `sonarcloud` job'u
çalışır. İlk run ~5-7 dakika sürer (Sonar tüm tarihçeyi blame analiz
eder). Sonraki run'lar 1-2 dakika.

Bittikten sonra:
- <https://sonarcloud.io/project/overview?id=yigitsheker_finans-portali>
- PR'larda otomatik yorum + Quality Gate badge

## 5. README badge (opsiyonel)

Quality gate yeşil olunca README'ye eklemek için:

```markdown
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=yigitsheker_finans-portali&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=yigitsheker_finans-portali)
```

## Sorun giderme

**"Project not found"** hatası: `sonar-project.properties` içindeki
`sonar.projectKey` ve `sonar.organization` değerleri SonarCloud'da
yaratılan project'in ID'sine eşleşmiyor. Step 1'de gördüğünüz değerlerle
karşılaştırın.

**"Coverage missing"**: Backend test'leri çalışmadan scan başlatıldı
demektir. CI yaml'daki step sırası: `mvnw verify` → `SonarCloud scan`.
Lokal'de manuel çalıştırırken `mvn verify` adımını atlamayın.

**Forks / external contributor PR'ları**: SONAR_TOKEN secret'ına erişimi
yok, scan job'u skip eder (CI yaml'daki `if:` koşulu). Maintainer
ben-merge edince main push'unda scan çalışır.
