"""Arithmetic reference only; supplied judgments are not fact-checked."""
from dataclasses import dataclass
from decimal import Decimal as D, Context, localcontext, ROUND_HALF_EVEN, ROUND_HALF_UP
from functools import wraps
from types import MappingProxyType

AXES = ('F', 'S', 'I', 'T', 'C', 'R', 'E')
PAPER_WEIGHTS = MappingProxyType(dict(zip(AXES, map(D, ('0.25', '0.20', '0.10', '0.10', '0.10', '0.10', '0.15')))))
POLICY_VERSION = 'reference-v1'

def reference_precision(function):
    @wraps(function)
    def wrapped(*args, **kwargs):
        with localcontext(Context(prec=28, rounding=ROUND_HALF_EVEN)):
            return function(*args, **kwargs)
    return wrapped

def unit(value):
    if not isinstance(value, D) or not value.is_finite() or not D(0) <= value <= D(1):
        raise ValueError('score must be a finite Decimal in [0, 1]')
    return value

@dataclass(frozen=True)
class Bounds:
    lower: D
    upper: D
    def __post_init__(self):
        if unit(self.lower) > unit(self.upper):
            raise ValueError('lower exceeds upper')

@reference_precision
def fraction_bounds(positive, negative, unknown):
    counts = (positive, negative, unknown)
    if any(type(n) is not int or n < 0 for n in counts):
        raise ValueError('counts must be nonnegative integers')
    total = sum(counts)
    return Bounds(D(positive) / total, D(positive + unknown) / total) if total else None

@reference_precision
def score_bounds(scores, weights=PAPER_WEIGHTS, stage='provisional'):
    if set(scores) != set(AXES) or set(weights) != set(AXES):
        raise ValueError('exactly seven axes are required')
    if stage not in ('provisional', 'reviewed'):
        raise ValueError('invalid stage')
    if sum(unit(w) for w in weights.values()) != D(1):
        raise ValueError('weights must sum to one')
    if any(v is not None and not isinstance(v, Bounds) for v in scores.values()):
        raise ValueError('invalid bounds')
    axes = AXES[:-1] if stage == 'provisional' else AXES
    denominator = sum(weights[a] for a in axes)
    if not denominator:
        raise ValueError('active weights must be positive')
    if any(scores[a] is None for a in axes):
        return None
    lower = upper = D(0)
    for axis in axes:
        lo, hi = scores[axis].lower, scores[axis].upper
        if axis == 'R':
            lo, hi = D(1) - hi, D(1) - lo
        lower += lo * weights[axis]
        upper += hi * weights[axis]
    return Bounds(lower / denominator, upper / denominator)

@reference_precision
def paired_change(before, after):
    if not before or len(before) != len(after):
        raise ValueError('nonempty paired lists of equal length are required')
    pre = sum(unit(v) for v in before) / len(before)
    post = sum(unit(v) for v in after) / len(after)
    return {'points': (post - pre) * 100,
            'relative_percent': (post - pre) / pre * 100 if pre else None}

@reference_precision
def display(value, places=1):
    if not isinstance(value, D) or not value.is_finite() or type(places) is not int or not 0 <= places <= 6:
        raise ValueError('invalid display input')
    return str(value.quantize(D(1).scaleb(-places), rounding=ROUND_HALF_UP))

@reference_precision
def serialize_bounds(bounds):
    if bounds is None:
        return None
    return {'lower': str(bounds.lower), 'upper': str(bounds.upper),
            'lower_percent': display(bounds.lower * 100),
            'upper_percent': display(bounds.upper * 100)}
