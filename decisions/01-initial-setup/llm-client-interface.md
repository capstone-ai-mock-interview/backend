# 기술 결정: LLMClient 인터페이스

관련 이슈: #3 Spring Boot 프로젝트 초기 환경 세팅

## 결정: LLM 호출을 인터페이스로 추상화

## 왜 만들었는지

1. 병렬 개발 가능: AWS Bedrock 연동이 안 된 상태에서도 팀원들이 각자 서비스(ResumeService, QuestionGenerator 등)를 개발할 수 있다. llmClient.invoke()만 호출하면 되니까.

2. 중복 제거: LLM을 호출하는 곳이 4군데(ResumeService, QuestionGenerator, FollowUpEngine, AnswerEvaluator)인데, 각자 Bedrock 호출 코드를 짜는 대신 공통 인터페이스 하나로 통일.

3. 구현체 교체 용이: 나중에 Claude → Titan, 또는 경량/무거운 모델을 서비스별로 다르게 쓸 때 서비스 코드 수정 없이 구현체만 바꾸면 된다. (@Qualifier로 구분)

4. 테스트 용이: 실제 Bedrock 호출 없이 가짜 응답을 돌려주는 Mock으로 테스트 가능.

## 구조

```
LLMClient (인터페이스) ← 지금 만든 것
  ├── BedrockClaudeClient (구현체) ← 나중에 만들 것
  ├── BedrockHaikuClient (구현체) ← 필요 시
  └── MockLLMClient (테스트용) ← 필요 시
```
