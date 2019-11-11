package com.rbkmoney.shumpune.dao;

import com.rbkmoney.shumpune.dao.mapper.BalanceModelMapper;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.exception.DaoException;
import org.springframework.core.NestedRuntimeException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AccountLogDaoImpl extends NamedParameterJdbcDaoSupport implements AccountLogDao {

    private final RowMapper<BalanceModel> balanceRowMapper;

    public AccountLogDaoImpl(DataSource ds, BalanceModelMapper balanceModelMapper) {
        setDataSource(ds);
        this.balanceRowMapper = balanceModelMapper;
    }

    @Override
    public Long insert(BalanceModel balanceModel) {
        final String sql =
                "INSERT INTO shm.account_log(account_id, own_amount, max_available_amount, " +
                        "min_available_amount, clock, creation_time) " +
                        "VALUES (:accountId, :ownAmount, :maxAvailableAmount, :minAvailableAmount, :clock, :creationTime) " +
                        "RETURNING id;";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("accountId", balanceModel.getAccountId());
        params.addValue("ownAmount", balanceModel.getOwnAmount());
        params.addValue("maxAvailableAmount", balanceModel.getMaxAvailableAmount());
        params.addValue("minAvailableAmount", balanceModel.getMinAvailableAmount());
        params.addValue("clock", balanceModel.getClock());
        params.addValue("creationTime", LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), Types.OTHER);
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            int updateCount = getNamedParameterJdbcTemplate()
                    .update(sql, params, keyHolder);
            if (updateCount != 1) {
                throw new DaoException("Account_log creation returned unexpected update count: " + updateCount);
            }
            return keyHolder.getKey().longValue();
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public BalanceModel getLastBalanceById(Long id, Long clockId) {
        MapSqlParameterSource params = new MapSqlParameterSource("accId", id).addValue("clockId", clockId);
        final String sql =
                "with max_clock_account as " +
                        "(select account_id, max(clock) maxClock" +
                        "    from shm.account_log" +
                        "    where clock <= :clockId" +
                        "    group by account_id" +
                        "    having account_id = :accId) " +
                        "select shm.account_log.id, shm.account_log.account_id, shm.account_log.own_amount, shm.account_log.max_available_amount, " +
                        " shm.account_log.min_available_amount, shm.account_log.clock " +
                        "from   max_clock_account " +
                        "inner join shm.account_log " +
                        "on         shm.account_log.account_id = max_clock_account.account_id " +
                        "and        shm.account_log.clock = max_clock_account.maxClock";
        try {
            List<BalanceModel> query = getNamedParameterJdbcTemplate()
                    .query(sql, params, balanceRowMapper);
            if (!query.isEmpty()) {
                return query.get(0);
            }
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }


}
