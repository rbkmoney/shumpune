package com.rbkmoney.shumpune.dao;

import com.rbkmoney.damsel.shumpune.AccountPrototype;
import com.rbkmoney.geck.common.util.TypeUtil;
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

@Service
public class AccountDaoImpl extends NamedParameterJdbcDaoSupport implements AccountDao {

    private final RowMapper<BalanceModel> balanceRowMapper;

    public AccountDaoImpl(DataSource ds, BalanceModelMapper balanceModelMapper) {
        setDataSource(ds);
        this.balanceRowMapper = balanceModelMapper;
    }

    @Override
    public Long insert(AccountPrototype prototype) {
        final String sql = "INSERT INTO shm.account(curr_sym_code, creation_time, description) " +
                "VALUES (:curr_sym_code, :creation_time, :description) RETURNING id;";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("curr_sym_code", prototype.getCurrencySymCode());
        params.addValue("creation_time", getInstant(prototype), Types.OTHER);
        params.addValue("description", prototype.getDescription());
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            int updateCount = getNamedParameterJdbcTemplate()
                    .update(sql, params, keyHolder);
            if (updateCount != 1) {
                throw new DaoException("Account creation returned unexpected update count: " + updateCount);
            }
            return keyHolder.getKey().longValue();
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    private LocalDateTime getInstant(AccountPrototype prototype) {
        return toLocalDateTime(prototype.isSetCreationTime() ? TypeUtil.stringToInstant(prototype.getCreationTime()) : Instant.now());
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    public BalanceModel getBalanceById(Long id, Long clock) {
        if (id != null && clock != null) {
            final String sql = "select ";
            try {
                BalanceModel balanceModel = getJdbcTemplate()
                        .queryForObject(sql, balanceRowMapper);
                if (balanceModel != null && balanceModel.getClock() >= clock) {
                    return balanceModel;
                }
            } catch (NestedRuntimeException e) {
                throw new DaoException(e);
            }
        }
        return null;
    }

}
