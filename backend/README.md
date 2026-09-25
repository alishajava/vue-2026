# document-backend

vue-2026 프론트(`src/api/documentApi.js`)가 호출하는 `/api/documents/*` 엔드포인트를
그대로 구현한 스프링 부트 백엔드. 파일은 DB에 바이너리로 저장하고, 구버전 `.ppt`/
`.pptx`는 Apache POI로 PNG 변환해서 내려준다 (LibreOffice/VM 불필요).

개발/테스트 용도이므로 **Docker 없이, JDK만으로** 돌아가도록 기본 설정을 잡아뒀다
(내장 DB인 H2 사용 - 별도 DB 서버/컨테이너 자체가 필요 없음). 저사양 노트북에서
Docker Desktop이 버거우면 이 경로를 쓰면 된다. Docker/Postgres가 편하면 그것도
그대로 지원한다 (아래 "Docker로 실행하기" 참고).

## 로컬에서 실행하기 (Docker 없음, 필요 비용 $0)

### 0) JDK 17 설치 (한 번만)

Docker Desktop과 달리 백그라운드 가상머신을 띄우지 않는 가벼운 런타임이다.

- Mac: `brew install openjdk@17`
- Windows: `winget install EclipseAdoptium.Temurin.17.JDK`
- Linux: `sudo apt install openjdk-17-jdk`

Maven도 필요하다: Mac `brew install maven`, Windows `winget install Apache.Maven`, Linux `sudo apt install maven`.

### 1) 백엔드 띄우기

```bash
cd document-backend
mvn spring-boot:run
```

첫 실행은 의존성을 내려받느라 좀 걸리고, 이후엔 빠르다. 저장 데이터는
`document-backend/data/documentdb.mv.db` 파일 하나에 그대로 쌓인다(DB 서버 없음).

`http://localhost:8080/api/documents`에 접속해서 `[]`(빈 배열)가 나오면 정상 동작.
(데이터 내용을 직접 보고 싶으면 `http://localhost:8080/h2-console`에서 JDBC URL을
`application.yml`의 `spring.datasource.url`과 동일하게 입력하면 테이블을 조회할 수 있다.)

### 2) 프론트(vue-2026)에서 이 백엔드를 보게 하기

`vue-2026` 저장소 루트에 `.env.local` 파일을 만든다 (git에 커밋되지 않는 로컬 전용 설정):

```
VITE_API_BASE_URL=http://localhost:8080
```

그 다음 평소대로 `npm run dev`로 프론트를 실행하면, `/#/documents` 화면이 로컬
백엔드와 통신한다.

### 3) 끝나면

터미널에서 `Ctrl+C`로 멈추면 된다. `data/` 폴더에 저장된 내용은 다음에 다시
`mvn spring-boot:run` 하면 그대로 남아있다. 완전히 초기화하고 싶으면 `data/` 폴더를
지우면 된다.

## Docker로 실행하기 (선택 - Postgres를 쓰고 싶거나 노트북이 넉넉할 때)

```bash
cd document-backend
cp .env.example .env
docker compose up -d --build
```

`docker-compose.yml`은 PostgreSQL 컨테이너 + 백엔드를 같이 띄우고, 백엔드가
`postgres` 프로필로 자동 전환되어 H2 대신 Postgres를 쓴다. 끝나면:

```bash
docker compose down        # 컨테이너만 정지 (데이터는 유지)
docker compose down -v     # 데이터까지 완전히 삭제하고 싶을 때
```

## 엔드포인트

| 메서드 | 경로 | 설명 |
|---|---|---|
| GET | `/api/documents` | 목록 (파일 바이너리 제외) |
| GET | `/api/documents/{id}` | 단건 메타데이터 |
| GET | `/api/documents/{id}/file` | `{ fileBase64 }` |
| POST | `/api/documents` | 등록 |
| PUT | `/api/documents/{id}` | 변경 |
| DELETE | `/api/documents/{id}` | 삭제 |
| POST | `/api/documents/convert` | 저장 전 파일 즉석 변환 (`.ppt`/`.pptx` → PNG 배열) |
| GET | `/api/documents/{id}/slides` | 저장된 문서 변환 (`.ppt`/`.pptx` → PNG 배열) |

## 배포된 사이트에서도 테스트하고 싶다면 (선택, 비용 $0 - Oracle Cloud Always Free)

지금 당장은 필요 없지만, 나중에 GitHub Pages에 배포된 실제 프론트에서도 이 백엔드를
써보고 싶어지면 아래 순서로 진행하면 된다. Oracle Cloud의 "Always Free" 리소스는
체험판이 아니라 **평생 무료 티어**다. 신용카드 등록은 본인 확인용으로 필수지만,
Always Free 범위 안에서만 쓰면 과금되지 않는다.

<details>
<summary>펼치기</summary>

### 1) 계정 생성 + 안전장치부터

1. https://www.oracle.com/cloud/free/ 에서 가입
2. 가입 직후 **Billing → Budgets**에서 예산 알림(예: $1)을 걸어둔다
3. **"Upgrade to Pay As You Go" 버튼을 절대 누르지 않는다** - Free Tier 계정 상태를 유지해야 과금 원천 차단됨

### 2) 무료 VM 생성

1. **Compute → Instances → Create Instance**
2. Shape: **VM.Standard.A1.Flex** (ARM, Always Free 대상 - OCPU 4개/RAM 24GB까지 무료, 이 프로젝트엔 OCPU 1~2개면 충분)
3. 이미지: Ubuntu 22.04
4. SSH 공개키 등록
5. **Networking → Virtual Cloud Network → Security List**에서 포트 8080 인바운드 허용 추가

### 3) VM에 배포

```bash
ssh -i <키> ubuntu@<VM 공인 IP>
sudo iptables -I INPUT -p tcp --dport 8080 -j ACCEPT
sudo netfilter-persistent save
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
```

```bash
# 로컬에서 이 폴더를 VM으로 복사
scp -r -i <키> document-backend ubuntu@<VM 공인 IP>:~/
```

```bash
ssh -i <키> ubuntu@<VM 공인 IP>
cd document-backend
cp .env.example .env && nano .env   # DB_PASSWORD를 실제 비밀번호로 변경
docker compose up -d --build
```

### 4) CORS/프론트 설정

`application.yml`의 `app.cors.allowed-origin-pattern`에 배포된 프론트 주소를 추가:

```yaml
app:
  cors:
    allowed-origin-pattern: "http://localhost:*,https://alishajava.github.io"
```

프론트 배포 시 `VITE_API_BASE_URL=http://<VM 공인 IP>:8080`으로 빌드.

`http://IP:8080`은 브라우저에서 "안전하지 않음"으로 뜨고, HTTPS인 GitHub Pages에서
호출하면 Mixed Content로 막힐 수 있다. 필요하면 무료 도메인(DuckDNS) + Caddy로
간단히 HTTPS를 붙일 수 있다.

</details>
