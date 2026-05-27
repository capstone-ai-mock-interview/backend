import json
import sys
from pathlib import Path

sys.path.append(str(Path(__file__).resolve().parents[1]))

from shared.evaluator import EvaluationWorker


worker = EvaluationWorker()


def lambda_handler(event, context):
    processed = 0
    for record in event.get("Records", []):
        message = json.loads(record.get("body", "{}"))
        if message.get("type") != "SESSION_EVALUATION":
            continue
        worker.evaluate_session(message["sessionId"])
        processed += 1
    return {"processed": processed}
