package com.rbkmoney.shumpune.validator;

import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.damsel.shumpune.PostingBatch;
import com.rbkmoney.damsel.shumpune.PostingPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class FinalOpValidator {

    public static final String POSTING_PLAN_EMPTY = "Plan ($s) has no batches inside";
    public static final String POSTING_BATCH_EMPTY = "Posting batch (%d) has no postings inside";
    public static final String POSTING_BATCH_DUPLICATE = "Batch (%d) has duplicate in received list";
    public static final String POSTING_BATCH_ID_RANGE_VIOLATION = "Batch in plan (%d) is not allowed to have long MAX or MIN value";

    public void validate(PostingPlan postingPlan) throws InvalidRequest {
        Set<Long> batchIds = new HashSet<>();
        var batchList = postingPlan.getBatchList();
        if (batchList.isEmpty()) {
            log.warn("Plan {} has not batches inside", postingPlan.getId());
            throw new InvalidRequest(Arrays.asList(String.format(POSTING_PLAN_EMPTY, postingPlan.getId())));
        }
        for (PostingBatch postingBatch : batchList) {
            if (postingBatch.getPostingsSize() < 1) {
                log.warn("Batch {} has no postings inside", postingBatch.getId());
                throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_EMPTY, postingBatch.getId())));
            }
            if (!batchIds.add(postingBatch.getId())) {
                log.warn("Batch {} has duplicate in received list", postingBatch.getId());
                throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_DUPLICATE, postingBatch.getId())));
            }
        }
        if (batchIds.contains(Long.MIN_VALUE) || batchIds.contains(Long.MAX_VALUE)) {
            log.warn("Batch in plan {} is not allowed to have long MAX or MIN value", postingPlan.getId());
            throw new InvalidRequest(Arrays.asList(String.format(POSTING_BATCH_ID_RANGE_VIOLATION, postingPlan.getId())));
        }
    }

}