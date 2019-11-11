package com.rbkmoney.shumpune.integration;

import com.google.common.util.concurrent.Futures;
import com.rbkmoney.damsel.shumpune.*;
import com.rbkmoney.shumpune.DaoTestBase;
import com.rbkmoney.shumpune.ShumpuneApplication;
import com.rbkmoney.shumpune.handler.ShumpuneServiceHandler;
import com.rbkmoney.shumpune.utils.AccountGenerator;
import com.rbkmoney.shumpune.utils.PostingGenerator;
import com.rbkmoney.shumpune.utils.VectorClockSerializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShumpuneApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ConsistencyTest extends DaoTestBase {

    @RequiredArgsConstructor
    private class ExecutePlan implements Callable<Map.Entry<Long, Balance>> {

        private final ShumpuneServiceHandler serviceHandler;
        private final PostingPlanChange postingPlanChange;
        private final RetryTemplate retryTemplate;

        @Override
        public Map.Entry<Long, Balance> call() throws Exception {
            Clock hold = retryTemplate.execute(context -> serviceHandler.hold(postingPlanChange));
            Balance balanceByID = retryTemplate.execute(context -> serviceHandler.getBalanceByID(1L, hold));
            return Map.entry(VectorClockSerializer.deserialize(balanceByID.getClock().getVector()), balanceByID);
        }
    }

    private static final int ATTEMPTS = 10000;
    private static final int THREAD_NUM = 16;
    private static final long HOLD_AMOUNT = 100;

    @Autowired
    ShumpuneServiceHandler serviceHandler;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationContext applicationContext;

    RetryTemplate retryTemplate = getRetryTemplate();

    @NotNull
    private RetryTemplate getRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        FixedBackOffPolicy fixedBackOffPolicy = new FixedBackOffPolicy();
        fixedBackOffPolicy.setBackOffPeriod(100L);
        retryTemplate.setBackOffPolicy(fixedBackOffPolicy);
        return retryTemplate;
    }

    private ExecutorService executorService = Executors.newFixedThreadPool(THREAD_NUM);

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    public void getBalanceConcurrency() throws TException {
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(Instant.now());
        serviceHandler.createAccount(accountPrototype);
        serviceHandler.createAccount(accountPrototype);
        serviceHandler.createAccount(accountPrototype);

        List<Future<Map.Entry<Long, Balance>>> futureList = new ArrayList<>();


        for (int i = 0; i < ATTEMPTS; i++) {
            PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(i + "", 1L, 2L, 3L, HOLD_AMOUNT);
            futureList.add(executorService.submit(new ExecutePlan(
                    serviceHandler,
                    postingPlanChange,
                    retryTemplate)
            ));
        }

        List<Balance> orderedBalances = futureList.stream()
                .map(Futures::getUnchecked)
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());

        for (int i = 0; i < orderedBalances.size(); i++) {
            Balance balance = orderedBalances.get(i);
            long expectedBalance = -(i + 1) * HOLD_AMOUNT;
            assertEquals(
                    String.format("Wrong balance after hold, iteration='%d'", i),
                    expectedBalance, balance.getMinAvailableAmount()
            );
        }
    }
}
