package com.rbkmoney.shumpune.service;

import com.rbkmoney.damsel.base.InvalidRequest;
import com.rbkmoney.damsel.shumpune.Clock;
import com.rbkmoney.damsel.shumpune.PostingBatch;
import com.rbkmoney.damsel.shumpune.PostingPlan;
import com.rbkmoney.damsel.shumpune.PostingPlanChange;
import com.rbkmoney.shumpune.constant.PostingOperation;
import com.rbkmoney.shumpune.converter.PostingPlanToListPostingModelListConverter;
import com.rbkmoney.shumpune.converter.PostingPlanToPostingPlanInfoConverter;
import com.rbkmoney.shumpune.converter.PostingPlanToPostingPlanModelConverter;
import com.rbkmoney.shumpune.dao.PlanDaoImpl;
import com.rbkmoney.shumpune.domain.PostingModel;
import com.rbkmoney.shumpune.domain.PostingPlanInfo;
import com.rbkmoney.shumpune.domain.PostingPlanModel;
import com.rbkmoney.shumpune.utils.VectorClockSerializer;
import com.rbkmoney.shumpune.validator.FinalOpValidator;
import com.rbkmoney.shumpune.validator.HoldPlanValidator;
import com.rbkmoney.shumpune.validator.PostingBatchValidator;
import com.rbkmoney.shumpune.validator.PostingsUpdateValidator;
import lombok.RequiredArgsConstructor;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PostingPlanService {

    private final PostingPlanToPostingPlanModelConverter converter;
    private final PlanDaoImpl planDao;
    private final HoldPlanValidator holdPlanValidator;
    private final FinalOpValidator finalOpValidator;
    private final PostingBatchValidator postingBatchValidator;
    private final PostingPlanToPostingPlanInfoConverter postingPlanToPostingPlanInfoConverter;
    private final PostingsUpdateValidator postingsUpdateValidator;
    private final PostingPlanToListPostingModelListConverter postingPlanToListPostingModelListConverter;

    @Transactional
    public Clock hold(PostingPlanChange postingPlanChange) throws TException {
        holdPlanValidator.validate(postingPlanChange);
        postingBatchValidator.validate(postingPlanChange.getBatch());

        PostingPlanModel postingPlanModel = converter.convert(postingPlanChange);

        long clock = planDao.insertPostings(postingPlanModel.getPostingModels());
        postingPlanModel.getPostingPlanInfo().setClock(clock);
        planDao.addOrUpdatePlanLog(postingPlanModel);

        return Clock.vector(VectorClockSerializer.serialize(postingPlanModel.getPostingPlanInfo().getClock()));
    }

    @Transactional
    public Clock commit(PostingPlan postingPlan) throws TException {
        finalOpValidator.validate(postingPlan);
        for (PostingBatch postingBatch : postingPlan.getBatchList()) {
            postingBatchValidator.validate(postingBatch);
        }

        PostingPlanInfo oldPostingPlanInfo = planDao.selectForUpdatePlanLog(postingPlan.getId());
        if (oldPostingPlanInfo == null) {
            throw new InvalidRequest(Arrays.asList(String.format("Hold operation not found for plan: %s", postingPlan.getId())));
        }

        Map<Long, List<PostingModel>> postingLogs = planDao.getPostingLogs(oldPostingPlanInfo.getId(), oldPostingPlanInfo.getPostingOperation());
        postingsUpdateValidator.validate(postingPlan, postingLogs);


        long clock = planDao.insertPostings(postingPlanToListPostingModelListConverter.convert(postingPlan).stream()
                .peek(p -> p.setOperation(PostingOperation.COMMIT))
                .collect(Collectors.toList()));

        PostingPlanInfo newPostingPlanInfo = postingPlanToPostingPlanInfoConverter.convert(postingPlan);
        newPostingPlanInfo.setClock(clock);
        newPostingPlanInfo.setPostingOperation(PostingOperation.COMMIT);

        PostingPlanInfo updatePlanLog = planDao.updatePlanLog(newPostingPlanInfo);

        return Clock.vector(VectorClockSerializer.serialize(updatePlanLog.getClock()));
    }

}
