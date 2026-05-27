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
        if message.get("type") != "TURN_EVALUATION":
            continue
        worker.evaluate_turn(message["sessionId"], int(message["turnNumber"]))
        processed += 1
    return {"processed": processed}
