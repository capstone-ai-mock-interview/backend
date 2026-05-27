import importlib.util
import json
import sys
from pathlib import Path
from unittest import TestCase
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.append(str(ROOT))


def load_handler(path):
    spec = importlib.util.spec_from_file_location("handler_under_test", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class LambdaHandlerTest(TestCase):
    @patch("shared.evaluator.EvaluationWorker")
    def test_turn_handler_processes_turn_messages(self, worker_cls):
        worker = Mock()
        worker_cls.return_value = worker
        module = load_handler(ROOT / "turn-evaluator" / "handler.py")

        result = module.lambda_handler({
            "Records": [{"body": json.dumps({
                "type": "TURN_EVALUATION",
                "sessionId": "sess-1",
                "turnNumber": 2,
            })}]
        }, None)

        self.assertEqual({"processed": 1}, result)
        worker.evaluate_turn.assert_called_once_with("sess-1", 2)

    @patch("shared.evaluator.EvaluationWorker")
    def test_session_handler_processes_session_messages(self, worker_cls):
        worker = Mock()
        worker_cls.return_value = worker
        module = load_handler(ROOT / "session-evaluator" / "handler.py")

        result = module.lambda_handler({
            "Records": [{"body": json.dumps({
                "type": "SESSION_EVALUATION",
                "sessionId": "sess-1",
            })}]
        }, None)

        self.assertEqual({"processed": 1}, result)
        worker.evaluate_session.assert_called_once_with("sess-1")
