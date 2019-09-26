package com.rbkmoney.shumpune.dao;

import com.rbkmoney.damsel.shumpune.MigrationPostingPlan;
import com.rbkmoney.damsel.shumpune.Operation;
import com.rbkmoney.shumpune.constant.PostingLogFields;
import com.rbkmoney.shumpune.constant.PostingOperation;
import com.rbkmoney.shumpune.dao.mapper.PostingModelMapper;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.domain.PostingModel;
import com.rbkmoney.shumpune.exception.DaoException;
import org.springframework.core.NestedRuntimeException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcDaoSupport;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.rbkmoney.shumpune.utils.DaoUtils.checkBatchUpdate;

@Component
public class PlanDaoImpl extends NamedParameterJdbcDaoSupport implements PlanDao {

    private static final int BATCH_SIZE = 1000;

    public static final String OPERATION = "operation";

    public static final String SQL_GET_SUM_BY_ACC = "select sum(AMOUNT) as own_amount, operation " +
            "from shm.posting_log " +
            "where id > :fromClock and id <= :toClock " +
            "and %s = :acc_id " +
            "GROUP BY operation";

    private final PostingModelMapper postingModelMapper;

    public PlanDaoImpl(DataSource ds, PostingModelMapper postingModelMapper) {
        setDataSource(ds);
        this.postingModelMapper = postingModelMapper;
    }


    @Override
    public long insertPostings(List<PostingModel> postings) {
        final String sql = "INSERT INTO shm.posting_log(plan_id, batch_id, from_account_id, to_account_id, creation_time, amount, curr_sym_code, operation, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?::shm.posting_operation_type, ?)";

        int[][] updateCounts = getJdbcTemplate().batchUpdate(sql, postings, BATCH_SIZE,
                (ps, argument) -> {
                    ps.setString(1, argument.getPlanId());
                    ps.setLong(2, argument.getBatchId());
                    ps.setLong(3, argument.getAccountFromId());
                    ps.setLong(4, argument.getAccountToId());
                    ps.setTimestamp(5, Timestamp.from(argument.getCreationTime()));
                    ps.setLong(6, argument.getAmount());
                    ps.setString(7, argument.getCurrencySymbCode());
                    ps.setString(8, argument.getOperation().name());
                    ps.setString(9, argument.getDescription());
                });
        checkBatchUpdate(updateCounts);
        PostingModel postingModel = postings.get(0);
        return selectMaxClock(postingModel.getPlanId(), postingModel.getBatchId());
    }

    @Override
    public Map<Long, List<PostingModel>> getPostingLogs(String planId, PostingOperation operation) {
        final String sql = "select * from shm.posting_log where plan_id = :plan_id and operation = :operation::shm.posting_operation_type";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("plan_id", planId);
        params.addValue(OPERATION, operation.name());
        try {
            return getNamedParameterJdbcTemplate()
                    .query(sql, params, postingModelMapper).stream()
                    .collect(Collectors.groupingBy(PostingModel::getBatchId));
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    @Override
    public BalanceModel getBalance(Long accountId, Long fromClock, Long toClock) {
        MapSqlParameterSource params = new MapSqlParameterSource("fromClock", fromClock)
                .addValue("toClock", toClock)
                .addValue("acc_id", accountId);

        Map<String, Long> sumMapFrom = new HashMap<>();
        getNamedParameterJdbcTemplate()
                .query(String.format(SQL_GET_SUM_BY_ACC, PostingLogFields.FROM_ACCOUNT_ID), params, rs -> {
                    putSumToByOperation(sumMapFrom, rs);
                });

        Map<String, Long> sumMapTo = new HashMap<>();
        getNamedParameterJdbcTemplate()
                .query(String.format(SQL_GET_SUM_BY_ACC, PostingLogFields.TO_ACCOUNT_ID), params, rs -> {
                    putSumToByOperation(sumMapTo, rs);
        });

        return BalanceModel.builder()
                .accountId(accountId)
                .clock(toClock)
                .ownAmount(safeGetSum(sumMapTo, PostingOperation.COMMIT) - safeGetSum(sumMapFrom, PostingOperation.COMMIT))
                .minAvailableAmount(safeGetSum(sumMapTo, PostingOperation.COMMIT) -
                        (safeGetSum(sumMapFrom, PostingOperation.HOLD) - safeGetSum(sumMapFrom, PostingOperation.ROLLBACK)))
                .maxAvailableAmount((safeGetSum(sumMapTo, PostingOperation.HOLD) - safeGetSum(sumMapTo, PostingOperation.ROLLBACK))
                        - safeGetSum(sumMapFrom, PostingOperation.COMMIT))
                .build();
    }

    private void putSumToByOperation(Map<String, Long> sumMapFrom, ResultSet rs) throws SQLException {
        sumMapFrom.put(rs.getString(OPERATION), rs.getLong("own_amount"));
    }

    @Override
    public List<PostingModel> getPostingModelsPlanById(String planId) {
        final String sql = "select plan_id, batch_id, from_account_id, to_account_id, operation, " +
                "amount, creation_time, curr_sym_code, description " +
                "from shm.posting_log " +
                "where plan_id=:plan_id";
        MapSqlParameterSource params = new MapSqlParameterSource("plan_id", planId);
        try {
            return getNamedParameterJdbcTemplate()
                    .query(sql, params, postingModelMapper);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (NestedRuntimeException e) {
            throw new DaoException(e);
        }
    }

    private long safeGetSum(Map<String, Long> sumMapFrom, PostingOperation postingOperation) {
        return sumMapFrom.getOrDefault(postingOperation.name(), 0L);
    }

    @Override
    public long selectMaxClock(String planId, Long batchId) {
        MapSqlParameterSource params = new MapSqlParameterSource("planId", planId)
                .addValue("batchId", batchId);

        String sqlGetClock = "select max(id) as clock " +
                "from shm.posting_log " +
                "where plan_id = :planId and batch_id= :batchId";

        return queryForLong(params, sqlGetClock);
    }

    @Override
    public long getMaxClockByAccountId(Long id) {
        MapSqlParameterSource params = new MapSqlParameterSource("accId", id);

        String sqlGetClock = "select max(id) as clock " +
                "from shm.posting_log " +
                "where from_account_id = :accId or to_account_id= :accId";

        return queryForLong(params, sqlGetClock);
    }

    private long queryForLong(MapSqlParameterSource params, String sqlGetClock) {
        Long clock = getNamedParameterJdbcTemplate()
                .queryForObject(sqlGetClock, params, Long.class);
        return clock != null ? clock : 0L;
    }

    public void batchPlanInsert(List<MigrationPostingPlan> list) {
        List<PostingModel> postingModels = list.stream().map(this::convertMigrationPostingPlan).collect(Collectors.toList());

        this.insertPostings(postingModels);
    }

    private PostingModel convertMigrationPostingPlan(MigrationPostingPlan migrationPostingPlan) {
        return new PostingModel(
                migrationPostingPlan.getPlanId(),
                migrationPostingPlan.getBatchId(),
                migrationPostingPlan.getAccountFromId(),
                migrationPostingPlan.getAccountToId(),
                migrationPostingPlan.getAmount(),
                migrationPostingPlan.getCurrencySymbCode(),
                migrationPostingPlan.getDescription(),
                Instant.parse(migrationPostingPlan.getCreationTime()),
                this.getOperation(migrationPostingPlan.getOperation())
        );
    }

    private PostingOperation getOperation(Operation operation) {
        switch (operation) {
            case HOLD:
                return PostingOperation.HOLD;
            case COMMIT:
                return PostingOperation.COMMIT;
            case ROLLBACK:
                return PostingOperation.ROLLBACK;
            default:
                throw new RuntimeException();
        }
    }
}
