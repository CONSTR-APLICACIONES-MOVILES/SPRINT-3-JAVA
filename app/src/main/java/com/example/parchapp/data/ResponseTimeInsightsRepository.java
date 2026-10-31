package com.example.parchapp.data;

import com.example.parchapp.domain.model.ResponseTimeEstimate;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Reads the backend-owned BQ5 response-time estimates. */
public interface ResponseTimeInsightsRepository {
    void getEstimates(Callback<List<ResponseTimeEstimate>> callback);
}
