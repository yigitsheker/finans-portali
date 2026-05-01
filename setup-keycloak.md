# Keycloak Kurulum Adımları

## 1. Keycloak'ı Başlatın
```bash
cd backend
docker-compose up keycloak -d
```

## 2. Keycloak Admin Console'a Giriş Yapın
- URL: http://localhost:8081
- Username: admin
- Password: admin

## 3. Realm Oluşturun
1. Sol üstteki "Master" dropdown'dan "Create Realm" seçin
2. Realm name: `finans`
3. "Create" butonuna tıklayın

## 4. Client Oluşturun
1. Sol menüden "Clients" seçin
2. "Create client" butonuna tıklayın
3. Şu bilgileri girin:
   - Client ID: `finans-frontend`
   - Client type: `OpenID Connect`
   - "Next" butonuna tıklayın

4. Capability config:
   - Client authentication: OFF
   - Authorization: OFF
   - Standard flow: ON
   - Direct access grants: ON
   - "Next" butonuna tıklayın

5. Login settings:
   - Root URL: `http://localhost:5173`
   - Home URL: `http://localhost:5173`
   - Valid redirect URIs: `http://localhost:5173/*`
   - Valid post logout redirect URIs: `http://localhost:5173/*`
   - Web origins: `http://localhost:5173`
   - "Save" butonuna tıklayın

## 5. Test Kullanıcısı Oluşturun
1. Sol menüden "Users" seçin
2. "Add user" butonuna tıklayın
3. Username: `testuser`
4. Email: `test@finans.com`
5. "Create" butonuna tıklayın
6. "Credentials" tab'ına gidin
7. "Set password" butonuna tıklayın
8. Password: `test123`
9. Temporary: OFF
10. "Save" butonuna tıklayın

## 6. Frontend'i Başlatın
```bash
cd frontend
npm run dev
```

## 7. Tarayıcıda Test Edin
- http://localhost:5173 adresine gidin
- Username: testuser
- Password: test123
