package com.rbkmoney.shumpune.handler;

import com.rbkmoney.damsel.shumpune.*;
import com.rbkmoney.shumpune.converter.BalanceModelToBalanceConverter;
import com.rbkmoney.shumpune.converter.PostingModelToPostingBatchConverter;
import com.rbkmoney.shumpune.dao.AccountDao;
import com.rbkmoney.shumpune.dao.PlanDao;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.domain.PostingModel;
import com.rbkmoney.shumpune.exception.DaoException;
import com.rbkmoney.shumpune.service.PostingPlanService;
import com.rbkmoney.woody.api.flow.error.WUnavailableResultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShumpuneServiceHandler implements AccounterSrv.Iface {

    private final AccountDao accountDao;
    private final PlanDao planDao;
    private final BalanceModelToBalanceConverter balanceModelToBalanceConverter;
    private final PostingModelToPostingBatchConverter postingModelToPostingBatchConverter;
    private final PostingPlanService postingPlanService;

    @Override
    public Clock hold(PostingPlanChange postingPlanChange) throws TException {
        log.info("Start hold postingPlanChange: {}", postingPlanChange);
        try {
            return postingPlanService.hold(postingPlanChange);
        } catch (DaoException e) {
            //todo uncomment
//            log.error("Failed to hold e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to hold e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public Clock commitPlan(PostingPlan postingPlan) throws TException {
        log.info("Start commitPlan postingPlan: {}", postingPlan);
        try {
            return postingPlanService.commit(postingPlan);
        } catch (DaoException e) {
            log.error("Failed to commitPlan e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to commitPlan e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public Clock rollbackPlan(PostingPlan postingPlan) throws TException {
        log.info("Start rollbackPlan postingPlan: {}", postingPlan);
        try {
            return postingPlanService.rollback(postingPlan);
        } catch (DaoException e) {
            log.error("Failed to rollbackPlan e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to rollbackPlan e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public PostingPlan getPlan(String planId) throws PlanNotFound, TException {
        log.info("Start getPlan planId: {}", planId);
        try {
            List<PostingModel> postingModelsPlanById = planDao.getPostingModelsPlanById(planId);
            Map<Long, List<PostingModel>> collect = postingModelsPlanById.stream()
                    .collect(Collectors.groupingBy(PostingModel::getBatchId));
            List<PostingBatch> postingBatches = collect.entrySet().stream()
                    .map(entry -> postingModelToPostingBatchConverter.convert(entry.getValue()).setId(entry.getKey()))
                    .collect(Collectors.toList());
            PostingPlan postingPlan = new PostingPlan()
                    .setId(planId)
                    .setBatchList(postingBatches);
            log.info("Finish getPlan postingPlan: {}", postingPlan);
            return postingPlan;
        } catch (DaoException e) {
            log.error("Failed to getPlan e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to getPlan e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public Account getAccountByID(long accountId) throws TException {
        log.info("Start getAccountByID accountId: {}", accountId);
        try {
            Account account = accountDao.getAccountById(accountId).orElseThrow(AccountNotFound::new);
            log.info("Finish createAccount accountId: {}", account);
            return account;
        } catch (DaoException e) {
            log.error("Failed to getAccountById e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to getAccountById e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public Balance getBalanceByID(long accountId, Clock clock) throws TException {
        log.info("Start getBalanceByID accountId: {} clock: {}", accountId, clock);
        try {
            BalanceModel balance = postingPlanService.getBalanceById(accountId, clock);
            log.info("Finish getBalanceByID balance: {}", balance);
            return balanceModelToBalanceConverter.convert(balance);
        } catch (Exception e) {
//            log.error("Failed to getBalanceByID e: ", e);
            throw new TException(e);
        }
    }

    @Override
    public long createAccount(AccountPrototype accountPrototype) throws TException {
        log.info("Start createAccount prototype: {}", accountPrototype);
        try {
            Long accountId = accountDao.insert(accountPrototype);
            log.info("Finish createAccount accountId: {}", accountId);
            return accountId;
        } catch (DaoException e) {
            log.error("Failed to create account e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to create account e: ", e);
            throw new TException(e);
        }
    }

}
