# 기술 결정: Docker Compose (로컬 DB 환경)

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 결정: Docker + PostgreSQL 17 + pgvector

## DB 환경 선택지

| 방식 | 장점 | 단점 |
|------|------|------|
| Docker + PostgreSQL (채택) | 팀원 전원 동일 환경, Docker 경험 | Docker Desktop 설치 필요 |
| Supabase | 설치 없음, 팀 공유 DB | 기술적 깊이 부족, 무료 한도 |
| 직접 설치 | Docker 불필요 | 팀원마다 환경 다를 수 있음, pgvector 설치 번거로움 |

## PostgreSQL 버전 선택

| 기준 | pg16 | pg17 |
|------|------|------|
| 출시 | 2023.09 | 2024.09 |
| 지원 종료 | 2028.11 | 2029.11 |
| SQL 문법/사용법 | 동일 | 동일 |
| 성능 | 기준 | 쓰기 성능 향상, 병렬 쿼리 확장 |
| pgvector 지원 | O | O |

pg17 선택 근거: 사용법이 완전히 동일하고 상위호환이라 최신 버전을 쓰지 않을 이유가 없음.

## 이미지: pgvector/pgvector:pg17
- PostgreSQL 17 + pgvector 확장이 미리 설치된 Docker 이미지
- 벡터 유사도 검색(RAG 파이프라인)에 pgvector 필요

## volume 설정
- 현재 ddl-auto: create라 앱 재시작 시 테이블 재생성됨
- volume은 나중에 ddl-auto: update로 전환 시 데이터 유지를 위해 미리 설정
