import json
import os
from contextlib import contextmanager
from urllib.parse import urlparse

import boto3
import psycopg2
from psycopg2.extras import RealDictCursor


TURN_SYSTEM_PROMPT = """
You are a senior technical interviewer evaluating a junior developer interview.
Return only a JSON object. Do not use markdown.
Write every user-facing value in Korean.

Schema:
{
  "individual_feedback": "concrete feedback under 150 Korean characters",
  "model_answer": "interview-ready model answer under 180 Korean characters"
}
"""

SESSION_SYSTEM_PROMPT = """
You are a senior technical interviewer summarizing an interview result.
Return only a JSON object. Do not use markdown.
Write every user-facing value in Korean.

Schema:
{
  "total_feedback": "overall feedback under 400 Korean characters",
  "overall_score": "상|중|하",
  "strength_types": [{"type": "category", "comment": "short comment"}],
  "weakness_types": [{"type": "category", "comment": "short comment"}],
  "competency_chart": {
    "학습력": 3,
    "문제해결력": 3,
    "협업능력": 3,
    "기술역량": 3,
    "주도성": 3,
    "스트레스내성": 3,
    "직무적합도": 3
  }
}
"""


class EvaluationWorker:
    def __init__(self):
        self.region = os.getenv("AWS_REGION", "us-east-1")
        self.model_id = os.getenv("LLM_MODEL", "us.anthropic.claude-sonnet-4-6")
        self.max_tokens = int(os.getenv("LLM_MAX_TOKENS", "1200"))
        self.temperature = float(os.getenv("LLM_TEMPERATURE", "0.2"))
        self.bedrock = boto3.client("bedrock-runtime", region_name=self.region)

    @contextmanager
    def db(self):
        conn = psycopg2.connect(**db_config())
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def evaluate_turn(self, session_id, turn_number):
        with self.db() as conn:
            qna = fetch_turn(conn, session_id, turn_number)
            if not qna:
                raise ValueError(f"QnA not found: {session_id}#{turn_number}")
            if has_text(qna["model_answer"]) and has_text(qna["individual_feedback"]):
                return "skipped"

            response = self.invoke(TURN_SYSTEM_PROMPT, build_turn_prompt(qna))
            parsed = parse_json(response)
            update_turn_feedback(
                conn,
                qna["id"],
                parsed.get("model_answer", ""),
                parsed.get("individual_feedback", ""),
            )
            return "saved"

    def evaluate_session(self, session_id):
        with self.db() as conn:
            interview = fetch_interview(conn, session_id)
            if not interview:
                raise ValueError(f"Interview not found: {session_id}")
            if interview["status"] == "FAILED":
                return "skipped"

            if interview["mode"] == "GROUP":
                return self.evaluate_group_participants(conn, interview)

            if has_text(interview["total_feedback"]):
                return "skipped"

            qnas = fetch_session_qnas(conn, interview["id"])
            if not qnas:
                return "skipped"
            self.ensure_turn_feedback(conn, qnas)

            qnas = fetch_session_qnas(conn, interview["id"])
            response = self.invoke(SESSION_SYSTEM_PROMPT, build_total_prompt(interview, qnas))
            parsed = parse_json(response)
            update_interview_total(conn, interview["id"], combined_feedback(parsed))
            return "saved"

    def evaluate_group_participants(self, conn, interview):
        saved = 0
        participants = fetch_participants(conn, interview["id"])
        for participant in participants:
            if has_text(participant["total_feedback"]):
                continue
            qnas = fetch_participant_qnas(conn, interview["id"], participant["member_id"])
            if not qnas:
                continue
            self.ensure_turn_feedback(conn, qnas)
            qnas = fetch_participant_qnas(conn, interview["id"], participant["member_id"])
            response = self.invoke(SESSION_SYSTEM_PROMPT, build_total_prompt(interview, qnas))
            parsed = parse_json(response)
            update_participant_total(
                conn,
                participant["id"],
                combined_feedback(parsed, include_types=False),
                parsed.get("overall_score", "N/A"),
            )
            saved += 1
        return f"saved:{saved}"

    def ensure_turn_feedback(self, conn, qnas):
        for qna in qnas:
            if has_text(qna["model_answer"]) and has_text(qna["individual_feedback"]):
                continue
            response = self.invoke(TURN_SYSTEM_PROMPT, build_turn_prompt(qna))
            parsed = parse_json(response)
            update_turn_feedback(
                conn,
                qna["id"],
                parsed.get("model_answer", ""),
                parsed.get("individual_feedback", ""),
            )

    def invoke(self, system_prompt, user_prompt):
        body = {
            "anthropic_version": "bedrock-2023-05-31",
            "max_tokens": self.max_tokens,
            "temperature": self.temperature,
            "system": system_prompt,
            "messages": [{"role": "user", "content": [{"type": "text", "text": user_prompt}]}],
        }
        response = self.bedrock.invoke_model(
            modelId=self.model_id,
            body=json.dumps(body).encode("utf-8"),
            contentType="application/json",
            accept="application/json",
        )
        payload = json.loads(response["body"].read())
        return payload["content"][0]["text"]


def db_config():
    db_url = os.getenv("DB_URL")
    if db_url:
        if db_url.startswith("jdbc:"):
            db_url = db_url.removeprefix("jdbc:")
        parsed = urlparse(db_url)
        return {
            "host": parsed.hostname,
            "port": parsed.port or 5432,
            "dbname": parsed.path.lstrip("/"),
            "user": parsed.username or os.getenv("DB_USERNAME"),
            "password": parsed.password or os.getenv("DB_PASSWORD"),
        }
    return {
        "host": os.getenv("DB_HOST"),
        "port": int(os.getenv("DB_PORT", "5432")),
        "dbname": os.getenv("DB_NAME"),
        "user": os.getenv("DB_USERNAME"),
        "password": os.getenv("DB_PASSWORD"),
    }


def fetch_interview(conn, session_id):
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute("SELECT * FROM interviews WHERE session_id = %s", (session_id,))
        return cur.fetchone()


def fetch_turn(conn, session_id, turn_number):
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            """
            SELECT q.*, i.category, i.session_id
            FROM interview_qnas q
            JOIN interviews i ON i.id = q.interview_id
            WHERE i.session_id = %s AND q.sequence_number = %s
            """,
            (session_id, turn_number),
        )
        return cur.fetchone()


def fetch_session_qnas(conn, interview_id):
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            """
            SELECT q.*, i.category, i.session_id
            FROM interview_qnas q
            JOIN interviews i ON i.id = q.interview_id
            WHERE q.interview_id = %s
              AND q.question_content IS NOT NULL
              AND q.question_content <> ''
            ORDER BY q.sequence_number ASC
            """,
            (interview_id,),
        )
        return list(cur.fetchall())


def fetch_participant_qnas(conn, interview_id, member_id):
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            """
            SELECT q.*, i.category, i.session_id
            FROM interview_qnas q
            JOIN interviews i ON i.id = q.interview_id
            WHERE q.interview_id = %s
              AND q.respondent_member_id = %s
              AND q.question_content IS NOT NULL
              AND q.question_content <> ''
            ORDER BY q.sequence_number ASC
            """,
            (interview_id, member_id),
        )
        return list(cur.fetchall())


def fetch_participants(conn, interview_id):
    with conn.cursor(cursor_factory=RealDictCursor) as cur:
        cur.execute(
            "SELECT * FROM interview_participants WHERE interview_id = %s ORDER BY joined_at ASC",
            (interview_id,),
        )
        return list(cur.fetchall())


def update_turn_feedback(conn, qna_id, model_answer, individual_feedback):
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE interview_qnas
            SET model_answer = %s, individual_feedback = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (model_answer, individual_feedback, qna_id),
        )


def update_interview_total(conn, interview_id, total_feedback):
    with conn.cursor() as cur:
        cur.execute(
            "UPDATE interviews SET total_feedback = %s, updated_at = NOW() WHERE id = %s",
            (total_feedback, interview_id),
        )


def update_participant_total(conn, participant_id, total_feedback, overall_score):
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE interview_participants
            SET total_feedback = %s, overall_score = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (total_feedback, overall_score, participant_id),
        )


def build_turn_prompt(qna):
    return f"""
Job/category:
{value_or_default(qna.get("category"), "General IT")}

Question:
{value_or_default(qna.get("question_content"), "None")}

Question intent:
{value_or_default(qna.get("intent"), "None")}

Follow-up:
{qna.get("is_follow_up")}

Candidate answer summary:
{answer_summary_text(qna.get("answer_summary"))}

Weak or missing point:
{value_or_default(qna.get("focus_point"), "None")}
"""


def build_total_prompt(interview, qnas):
    lines = [
        "Job/category:",
        value_or_default(interview.get("category"), "General IT"),
        "",
        f"Question count: {len(qnas)}",
        "",
        "Per-turn evaluations:",
    ]
    for qna in qnas:
        lines.extend(
            [
                f'{qna.get("sequence_number")}.',
                f'Question: {value_or_default(qna.get("question_content"), "None")}',
                f'Answer summary: {answer_summary_text(qna.get("answer_summary"))}',
                f'Individual feedback: {value_or_default(qna.get("individual_feedback"), "None")}',
                "",
            ]
        )
    return "\n".join(lines)


def answer_summary_text(raw):
    if not has_text(raw):
        return "No answer summary"
    try:
        parsed = json.loads(raw)
        if isinstance(parsed, list):
            return " ".join(str(item) for item in parsed if str(item).strip()) or "No answer summary"
    except Exception:
        pass
    return raw


def parse_json(value):
    text = (value or "").strip()
    if text.startswith("```"):
        text = text.replace("```json", "", 1).replace("```", "").strip()
    return json.loads(text)


def combined_feedback(parsed, include_types=True):
    total = parsed.get("total_feedback", "피드백 정보가 없습니다.")
    score = parsed.get("overall_score", "N/A")
    chart = json.dumps(parsed.get("competency_chart", {}), ensure_ascii=False)
    result = f"{total}\n\n[SCORE]\n{score}\n\n[CHART]\n{chart}"
    if include_types:
        strength = json.dumps(parsed.get("strength_types", []), ensure_ascii=False)
        weakness = json.dumps(parsed.get("weakness_types", []), ensure_ascii=False)
        result += f"\n\n[STRENGTH]\n{strength}\n\n[WEAKNESS]\n{weakness}"
    return result


def has_text(value):
    return value is not None and str(value).strip() != ""


def value_or_default(value, default):
    return value if has_text(value) else default
