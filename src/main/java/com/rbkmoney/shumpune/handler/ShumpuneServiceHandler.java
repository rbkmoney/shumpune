package com.rbkmoney.shumpune.handler;

import com.rbkmoney.damsel.shumpune.*;
import com.rbkmoney.shumpune.converter.BalanceModelToBalanceConverter;
import com.rbkmoney.shumpune.dao.AccountDao;
import com.rbkmoney.shumpune.dao.PlanDao;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.domain.PostingPlanModel;
import com.rbkmoney.shumpune.exception.DaoException;
import com.rbkmoney.shumpune.service.PostingPlanService;
import com.rbkmoney.shumpune.utils.VectorClockSerializer;
import com.rbkmoney.woody.api.flow.error.WUnavailableResultException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShumpuneServiceHandler implements AccounterSrv.Iface {

    private final AccountDao accountDao;
    private final PlanDao planDao;
    private final BalanceModelToBalanceConverter balanceModelToBalanceConverter;
    private final PostingPlanService postingPlanService;

    @Override
    public Clock hold(PostingPlanChange postingPlanChange) throws TException {
        log.info("Start hold postingPlanChange: {}", postingPlanChange);
        try {
            return postingPlanService.hold(postingPlanChange);
        } catch (DaoException e) {
            log.error("Failed to hold e: ", e);
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
        log.info("Start getPlan PLAN_ID: {}", planId);
        try {
            PostingPlanModel postingPlanModel = planDao.getPostingPlanById(planId);
            log.info("Finish getPlan accountId: {}", postingPlanModel);
            return null;
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
            log.error("Failed to create account", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to create account", e);
            throw new TException(e);
        }
    }

    @Override
    public Balance getBalanceByID(long accountId, Clock clock) throws AccountNotFound, ClockInFuture, TException {
        log.info("Start getBalanceByID accountId: {} CLOCK: {}", accountId, clock);
        try {
            Long clockValue = null;
            if (clock.isSetVector()) {
                clockValue = VectorClockSerializer.deserialize(clock.getVector());
            }
            BalanceModel balance = accountDao.getBalanceById(accountId, clockValue);
            log.info("Finish getBalanceByID balance: {}", balance);
            return balanceModelToBalanceConverter.convert(balance);
        } catch (DaoException e) {
            log.error("Failed to getBalanceByID e: ", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to getBalanceByID e: ", e);
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
            log.error("Failed to create account", e);
            throw new WUnavailableResultException(e);
        } catch (Exception e) {
            log.error("Failed to create account", e);
            throw new TException(e);
        }
    }

}
