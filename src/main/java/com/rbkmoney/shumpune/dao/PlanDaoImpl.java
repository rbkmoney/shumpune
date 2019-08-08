package com.rbkmoney.shumpune.dao;

import com.rbkmoney.shumpune.dao.mapper.PostingPlanInfoMapper;
import com.rbkmoney.shumpune.domain.PostingModel;
import com.rbkmoney.shumpune.domain.PostingPlanInfo;
import com.rbkmoney.shumpune.domain.PostingPlanModel;
import com.rbkmoney.shumpune.exception.DaoException;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;

@Component
public class PlanDaoImpl extends NamedParameterJdbcDaoSupport implements PlanDao {

    private static final int BATCH_SIZE = 1000;

    private final PostingPlanInfoMapper planRowMapper;

    public PlanDaoImpl(DataSource ds, PostingPlanInfoMapper planRowMapper) {
        setDataSource(ds);
        this.planRowMapper = planRowMapper;
    }

    @Override
    public PostingPlanModel addOrUpdatePlanLog(PostingPlanModel planLog) throws DaoException {
        final String sql =
                "insert into shm.plan_log (plan_id, last_batch_id, last_access_time, last_operation) " +
                        "values (:plan_id, :last_batch_id, :last_access_time, :last_operation::shm.posting_operation_type) " +
                        "on conflict (plan_id) " +
                        "do update set last_access_time=:last_access_time, last_operation=:last_operation::shm.posting_operation_type, last_batch_id=:last_batch_id " +
                        "where shm.plan_log.last_operation=:overridable_operation::shm.posting_operation_type " +
                        "returning *";

        MapSqlParameterSource params = createParams(planLog.getPostingPlanInfo());
        try {
            PostingPlanInfo postingPlanInfo = getNamedParameterJdbcTemplate().queryForObject(sql, params, planRowMapper);
            planLog.setPostingPlanInfo(postingPlanInfo);
            return planLog;
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    private MapSqlParameterSource createParams(PostingPlanInfo planLog) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planLog.getId());
        params.addValue("last_batch_id", planLog.getBatchId());
        params.addValue("last_operation", planLog.getPostingOperation().name());
        params.addValue("clock", planLog.getClock());
        return params;
    }

    @Override
    public long insertPostings(List<PostingModel> postings) {
        final String sql = "INSERT INTO shm.posting_log(plan_id, batch_id, from_account_id, to_account_id, amount, curr_sym_code, operation, description) VALUES (?, ?, ?, ?, ?, ?, ?::shm.posting_operation_type, ?)";

        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, postings, BATCH_SIZE,
                (ps, argument) -> {
                    ps.setString(1, argument.getPlanId());
                    ps.setLong(2, argument.getBatchId());
                    ps.setLong(3, argument.getAccountFromId());
                    ps.setLong(4, argument.getAccountToId());
                    ps.setLong(5, argument.getAmount());
                    ps.setString(6, argument.getCurrencySymbCode());
                    ps.setString(7, argument.getOperation().name());
                    ps.setString(8, argument.getDescription());
                });
        boolean checked = false;

        checkButchUpdate(updateCounts, checked);

        PostingModel postingModel = postings.get(0);

        return getClock(postingModel);
    }

    private long getClock(PostingModel postingModel) {
        MapSqlParameterSource params = new MapSqlParameterSource("planId", postingModel.planId)
                .addValue("batchId", postingModel.planId);

        String getClock = "select max(id) as clock " +
                "from shm.posting_log " +
                "where plan_id = :planId and batch_id= :batchId";

        return getNamedParameterJdbcTemplate().queryForObject(getClock, params, Long.class);
    }

    private void checkButchUpdate(int[][] updateCounts, boolean checked) {
        for (int i = 0; i < updateCounts.length; ++i) {
            for (int j = 0; j < updateCounts[i].length; ++j) {
                checked = true;
                if (updateCounts[i][j] != 1) {
                    throw new DaoException("Posting log creation returned unexpected update count: " + updateCounts[i][j]);
                }
            }
        }
        if (!checked) {
            throw new DaoException("Posting log creation returned unexpected update count [0]");
        }
    }
}
