package com.rbkmoney.shumpune.handler;

import com.rbkmoney.damsel.shumpune.Account;
import com.rbkmoney.damsel.shumpune.MigrationHelperSrv;
import com.rbkmoney.damsel.shumpune.MigrationPostingPlan;
import com.rbkmoney.shumpune.dao.AccountDaoImpl;
import com.rbkmoney.shumpune.dao.PlanDaoImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MigrationHelperHandler implements MigrationHelperSrv.Iface {

    private final AccountDaoImpl accountDao;
    private final PlanDaoImpl planDao;

    @Override
    public void migratePostingPlans(List<MigrationPostingPlan> list) throws TException {
        try {
            planDao.batchPlanInsert(list);
        } catch (Throwable e) {
            throw new TException(e);
        }
    }

    @Override
    public void migrateAccounts(List<Account> list) throws TException {
        try {
            accountDao.batchAccountInsert(list);
        } catch (Throwable e) {
            throw new TException(e);
        }
    }
}
