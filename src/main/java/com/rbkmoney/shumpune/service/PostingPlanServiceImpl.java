package com.rbkmoney.shumpune.service;

import com.rbkmoney.damsel.shumpune.Clock;
import com.rbkmoney.damsel.shumpune.PostingBatch;
import com.rbkmoney.damsel.shumpune.PostingPlan;
import com.rbkmoney.damsel.shumpune.PostingPlanChange;
import com.rbkmoney.damsel.shumpune.base.InvalidRequest;
import com.rbkmoney.shumpune.constant.PostingOperation;
import com.rbkmoney.shumpune.converter.PostingPlanToListPostingModelListConverter;
import com.rbkmoney.shumpune.converter.PostingPlanToPostingPlanModelConverter;
import com.rbkmoney.shumpune.dao.AccountLogDao;
import com.rbkmoney.shumpune.dao.PlanDaoImpl;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.domain.PostingModel;
import com.rbkmoney.shumpune.domain.PostingPlanModel;
import com.rbkmoney.shumpune.utils.VectorClockSerializer;
import com.rbkmoney.shumpune.validator.FinalOpValidator;
import com.rbkmoney.shumpune.validator.PostingBatchValidator;
import com.rbkmoney.shumpune.validator.PostingsUpdateValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostingPlanServiceImpl implements PostingPlanService {

    private final PostingPlanToPostingPlanModelConverter postingPlanToPostingPlanModelConverter;
    private final PlanDaoImpl planDao;
    private final AccountLogDao accountLogDao;
    private final FinalOpValidator finalOpValidator;
    private final PostingBatchValidator postingBatchValidator;
    private final PostingsUpdateValidator postingsUpdateValidator;
    private final PostingPlanToListPostingModelListConverter postingPlanToListPostingModelListConverter;

    @Override
    @Transactional
    public Clock hold(PostingPlanChange postingPlanChange) throws TException {
        postingBatchValidator.validate(postingPlanChange.getBatch(), postingPlanChange.getId());

        PostingPlanModel postingPlanModel = postingPlanToPostingPlanModelConverter.convert(postingPlanChange, PostingOperation.HOLD);

        Map<Long, List<PostingModel>> postingLogs = planDao.getPostingLogs(postingPlanChange.getId(), PostingOperation.HOLD);

        if (postingLogs.containsKey(postingPlanChange.getBatch().getId())) {
            postingsUpdateValidator.validate(List.of(postingPlanChange.getBatch()), postingLogs);
            log.info("This is duplicate request (HOLD), postingPlanChange: {}", postingPlanChange);
            return Clock.vector(VectorClockSerializer.serialize(
                    planDao.selectMaxClock(postingPlanChange.getId(), postingPlanChange.getBatch().getId())));
        }

        long clock = planDao.insertPostings(postingPlanModel.getPostingModels());

        return Clock.vector(VectorClockSerializer.serialize(clock));
    }

    @Override
    @Transactional
    public Clock commit(PostingPlan postingPlan) throws TException {
        return finalOperation(postingPlan, PostingOperation.COMMIT);
    }

    @Override
    @Transactional
    public Clock rollback(PostingPlan postingPlan) throws TException {
        return finalOperation(postingPlan, PostingOperation.ROLLBACK);
    }

    @Override
    public BalanceModel getBalanceById(Long accountId, Clock clock) throws TException {
        long clockValue = getClockValue(accountId, clock);

        BalanceModel lastBalanceById = accountLogDao.getLastBalanceById(accountId);

        long fromClock = 0L;
        if (lastBalanceById != null) {
            if (lastBalanceById.getClock() >= clockValue) {
                return lastBalanceById;
            }
            fromClock = lastBalanceById.getClock();
        }

        long maxClockForAccount = initMaxClockForAccount(accountId, clock, clockValue);

        BalanceModel balance = planDao.getBalance(accountId, fromClock, maxClockForAccount);
        if (lastBalanceById != null) {
            balance.setOwnAmount(lastBalanceById.getOwnAmount() + balance.getOwnAmount());
            balance.setMaxAvailableAmount(lastBalanceById.getMaxAvailableAmount() + balance.getMaxAvailableAmount());
            balance.setMinAvailableAmount(lastBalanceById.getMinAvailableAmount() + balance.getMinAvailableAmount());
        }

        Long idLog = accountLogDao.insert(balance);

        log.info("Created new balance log with for account: {} with id: {} ownAmount: {} minAmount: {} maxAmount: {} for clock: {}",
                accountId, idLog, balance.getOwnAmount(), balance.getMinAvailableAmount(), balance.getMaxAvailableAmount(), balance.getClock());

        return balance;
    }

    private long initMaxClockForAccount(Long id, Clock clock, long clockValue) {
        long maxClockByAccountId = clockValue;
        if (clock.isSetVector()) {
            maxClockByAccountId = planDao.getMaxClockByAccountId(id);
        }
        return maxClockByAccountId;
    }


    private long getClockValue(Long id, Clock clock) {
        long clockValue;
        if (clock.isSetVector()) {
            clockValue = VectorClockSerializer.deserialize(clock.getVector());
        } else {
            clockValue = planDao.getMaxClockByAccountId(id);
        }
        return clockValue;
    }

    private Clock finalOperation(PostingPlan postingPlan, PostingOperation postingOperation) throws TException {
        finalOpValidator.validate(postingPlan);
        for (PostingBatch postingBatch : postingPlan.getBatchList()) {
            postingBatchValidator.validate(postingBatch, postingPlan.getId());
        }

        List<PostingModel> postingModels = planDao.getPostingModelsPlanById(postingPlan.getId());

        if (postingModels.isEmpty()) {
            throw new InvalidRequest(Collections.singletonList(String.format("Hold OPERATION not found for plan: %s", postingPlan.getId())));
        }

        Map<Long, List<PostingModel>> postingLogsHolds = postingModels.stream()
                .filter(postingModel -> postingModel.getOperation().equals(PostingOperation.HOLD))
                .collect(Collectors.groupingBy(PostingModel::getBatchId));

        Map<Long, List<PostingModel>> postingLogsFinal = postingModels.stream()
                .filter(postingModel -> !postingModel.getOperation().equals(PostingOperation.HOLD))
                .collect(Collectors.groupingBy(PostingModel::getBatchId));

        if (!postingLogsFinal.isEmpty()) {
            postingsUpdateValidator.validate(postingPlan.getBatchList(), postingLogsFinal);
            log.info("This is duplicate request ({}), postingPlan: {}", postingOperation, postingPlan);
            PostingModel postingModel = postingModels.stream().max(Comparator.comparingLong(PostingModel::getBatchId))
                    .orElseThrow(); //never happens, but sonarqube complains
            return Clock.vector(VectorClockSerializer.serialize(planDao.selectMaxClock(postingPlan.getId(), postingModel.getBatchId())));
        }

        postingsUpdateValidator.validate(postingPlan.getBatchList(), postingLogsHolds);

        long clock = planDao.insertPostings(postingPlanToListPostingModelListConverter.convert(postingPlan, postingOperation));

        return Clock.vector(VectorClockSerializer.serialize(clock));
    }


}
