package com.rbkmoney.shumpune.handler;

import com.rbkmoney.damsel.shumpune.Account;
import com.rbkmoney.damsel.shumpune.InvalidPostingParams;
import com.rbkmoney.damsel.shumpune.MigrationBatch;
import com.rbkmoney.damsel.shumpune.MigrationHelperSrv;
import com.rbkmoney.damsel.shumpune.base.InvalidRequest;
import com.rbkmoney.shumpune.dao.AccountDaoImpl;
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

    @Override
    public void migratePostingPlans(List<MigrationBatch> list) throws InvalidPostingParams, InvalidRequest, TException {
        //todo WIP, next PR
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