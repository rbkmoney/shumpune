package com.rbkmoney.shumpune.dao;

import com.rbkmoney.shumpune.constant.PostingOperation;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.domain.PostingModel;

import java.util.List;
import java.util.Map;

public interface PlanDao {

    long insertPostings(List<PostingModel> postingModels);

    BalanceModel getBalance(Long accountId, Long fromClock, Long toClock);

    List<PostingModel> getPostingModelsPlanById(String planId);

    Map<Long, List<PostingModel>> getPostingLogs(String planId, PostingOperation operation);

    long selectMaxClock(String planId, Long batchId);

    long getMaxClockByAccountId(Long id);
}
