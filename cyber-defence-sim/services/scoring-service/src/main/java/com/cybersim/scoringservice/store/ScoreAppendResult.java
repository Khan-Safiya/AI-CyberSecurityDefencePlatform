package com.cybersim.scoringservice.store;

import com.cybersim.scoringservice.model.ScoreEventRecord;

public record ScoreAppendResult(ScoreEventRecord event, boolean created) {
}
