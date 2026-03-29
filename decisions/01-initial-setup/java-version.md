# 기술 결정: Java 버전

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 결정: Java 17

## 비교

| 기준 | Java 17 | Java 21 |
|------|---------|---------|
| LTS 지원 | ~2029.09 | ~2031.09 |
| Spring Boot 3.x 지원 | 전 버전 | 3.2+ |
| 레퍼런스/검색 자료 | 매우 풍부 | 늘어나는 중 |
| 주요 신규 기능 | record, sealed class, text block | virtual thread, pattern matching |
| 신규 기능의 프로젝트 필요성 | 충분 | 졸업 프로젝트 규모에서 체감 불가 |

## 핵심 근거
- Java 21 신규 기능(virtual thread 등)이 이 프로젝트에서 실질적으로 불필요
- 레퍼런스가 풍부해 에러 대응이 빠름
