from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict, Field, StrictInt, model_validator
from typing import Literal
from app.scoring import AXES, PAPER_WEIGHTS, POLICY_VERSION, fraction_bounds, score_bounds, serialize_bounds

app = FastAPI(title="Project Times internal arithmetic reference")

class Counts(BaseModel):
    model_config = ConfigDict(extra='forbid')
    positive: StrictInt = Field(ge=0, le=1000000)
    negative: StrictInt = Field(ge=0, le=1000000)
    unknown: StrictInt = Field(ge=0, le=1000000)

class Evaluation(BaseModel):
    model_config = ConfigDict(extra='forbid')
    axes: dict[str, Counts | None]
    stage: Literal['provisional', 'reviewed'] = 'provisional'

    @model_validator(mode='after')
    def validate_axes(self):
        if set(self.axes) != set(AXES):
            raise ValueError('exactly F,S,I,T,C,R,E are required')
        return self

def axis_result(counts, score):
    assessed = (fraction_bounds(counts.positive + counts.negative, counts.unknown, 0)
                if counts else None)
    coverage = serialize_bounds(assessed)
    return {'raw': serialize_bounds(score),
            'counts': counts.model_dump() if counts else None,
            'assessed_percent': coverage['lower_percent'] if coverage else None}

@app.get("/")
def root():
    return {"message": "Project Times internal arithmetic service", "fact_verification": False}

@app.get("/ready")
def ready():
    return {"status": "ok"}

@app.post('/evaluate')
def evaluate(request: Evaluation):
    scores = {axis: fraction_bounds(**counts.model_dump()) if counts else None
              for axis, counts in request.axes.items()}
    return {'policy_version': POLICY_VERSION, 'stage': request.stage,
            'kind': 'arithmetic_reference_not_fact_verification',
            'weights': {a: str(PAPER_WEIGHTS[a]) for a in AXES},
            'axes': {a: axis_result(request.axes[a], scores[a])
                     for a in AXES},
            'composite': serialize_bounds(score_bounds(scores, stage=request.stage)),
            'note': 'Ranges represent unresolved judgments, not confidence intervals or truth probabilities.'}
