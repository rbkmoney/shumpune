package com.rbkmoney.shumpune.dao;

import com.rbkmoney.shumpune.domain.BalanceModel;

public interface AccountLogDao {

    Long insert(BalanceModel balanceModel);

    BalanceModel getLastBalanceById(Long id, Long clockId);

}
