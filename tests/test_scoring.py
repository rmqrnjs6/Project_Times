from decimal import Decimal as D, getcontext
import pytest
from app.scoring import Bounds, AXES, PAPER_WEIGHTS, fraction_bounds, score_bounds, paired_change, display

def test_unknown_stays_in_denominator():
    assert fraction_bounds(6, 2, 2) == Bounds(D('.6'), D('.8'))

@pytest.mark.parametrize('counts', [(-1, 2, 3), (True, 2, 3), (1.5, 0, 0)])
def test_invalid_counts(counts):
    with pytest.raises(ValueError): fraction_bounds(*counts)

def test_empty_is_missing():
    assert fraction_bounds(0, 0, 0) is None

def test_risk_inversion_and_expert_exclusion():
    scores = dict.fromkeys(AXES, Bounds(D(1), D(1)))
    scores['R'] = Bounds(D(0), D(0)); scores['E'] = None
    assert score_bounds(scores) == Bounds(D(1), D(1))
    assert score_bounds(scores, stage='reviewed') is None

def test_risk_interval_endpoints():
    scores = dict.fromkeys(AXES, Bounds(D(1), D(1)))
    scores['R'] = Bounds(D('.2'), D('.7'))
    assert score_bounds(scores, stage='reviewed') == Bounds(D('.93'), D('.98'))

def test_required_missing_cannot_inflate_score():
    scores = dict.fromkeys(AXES, Bounds(D(1), D(1))); scores['F'] = None
    assert score_bounds(scores) is None

def test_bad_weights_rejected():
    weights = dict(PAPER_WEIGHTS); weights['F'] = D('.5')
    with pytest.raises(ValueError): score_bounds(dict.fromkeys(AXES, None), weights)

def test_context_isolation():
    original = getcontext().copy()
    try:
        getcontext().prec = 2
        assert fraction_bounds(1, 2, 0).lower == D('0.3333333333333333333333333333')
        assert getcontext().prec == 2
    finally:
        from decimal import setcontext
        setcontext(original)

def test_paired_change_and_zero_baseline():
    assert paired_change([D('.6')], [D('.75')]) == {'points': D(15), 'relative_percent': D(25)}
    assert paired_change([D(0)], [D(1)])['relative_percent'] is None

def test_half_up_display():
    assert display(D('89.25')) == '89.3'

@pytest.mark.parametrize('lo,hi', [('NaN', '1'), ('0.8', '0.6'), ('-0.1', '1')])
def test_invalid_bounds(lo, hi):
    with pytest.raises(ValueError): Bounds(D(lo), D(hi))
