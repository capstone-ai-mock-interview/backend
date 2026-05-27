# Evaluation Lambdas

Two SQS-triggered Lambdas are provided:

- `turn-evaluator`: consumes `TURN_EVALUATION` messages and fills `interview_qnas.model_answer` and `interview_qnas.individual_feedback`.
- `session-evaluator`: consumes `SESSION_EVALUATION` messages and fills `interviews.total_feedback`. For group interviews it also evaluates each participant into `interview_participants.total_feedback`.

## Environment variables

- `AWS_REGION`: AWS region, default `us-east-1`
- `LLM_MODEL`: Bedrock model id
- `LLM_MAX_TOKENS`: default `1200`
- `LLM_TEMPERATURE`: default `0.2`
- `DB_URL`: optional PostgreSQL URL or JDBC URL
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`: used when `DB_URL` is not provided

## Local checks

```bash
python -m unittest discover lambda/tests
```

Package each evaluator with `lambda/shared` and the matching handler directory. Install `requirements.txt` into the deployment artifact or Lambda layer.
