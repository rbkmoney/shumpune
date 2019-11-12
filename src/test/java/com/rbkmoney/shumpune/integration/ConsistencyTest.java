package com.rbkmoney.shumpune.integration;

import com.rbkmoney.damsel.shumpune.*;
import com.rbkmoney.shumpune.DaoTestBase;
import com.rbkmoney.shumpune.ShumpuneApplication;
import com.rbkmoney.shumpune.handler.ShumpuneServiceHandler;
import com.rbkmoney.shumpune.utils.AccountGenerator;
import com.rbkmoney.shumpune.utils.PostingGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShumpuneApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class ConsistencyTest extends DaoTestBase {

    @RequiredArgsConstructor
    private class ExecutePlan implements Callable<Map.Entry<Integer, Balance>> {

        private final AtomicInteger counter;
        private final ShumpuneServiceHandler serviceHandler;
        private final PostingPlanChange postingPlanChange;
        private final RetryTemplate retryTemplate;

        @Override
        public Map.Entry<Integer, Balance> call() throws Exception {
            try {
                Clock hold = retryTemplate.execute(context -> serviceHandler.hold(postingPlanChange));
                int i = counter.incrementAndGet();
                Balance balanceByID = retryTemplate.execute(context -> serviceHandler.getBalanceByID(1L, hold));
                return Map.entry(i, balanceByID);
            } catch (TException e) {
                e.printStackTrace();
                log.error("failed", e);
            }
            return null;
        }
    }

    private static final int ATTEMPTS = 1000000;

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

    private ExecutorService executorService = Executors.newFixedThreadPool(16);

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    public void doWorkConcurrentlyAndThenCheckTotalBalance() throws TException, ExecutionException, InterruptedException {
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(Instant.now());
        serviceHandler.createAccount(accountPrototype);
        serviceHandler.createAccount(accountPrototype);
        serviceHandler.createAccount(accountPrototype);

        AtomicInteger atomicInteger = new AtomicInteger(0);

        for (int j = 0; j < ATTEMPTS; j++) {
            PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(j + "", 1L, 2L, 3L, (long) 1);
            executorService.submit(new ExecutePlan(
                    atomicInteger,
                    serviceHandler,
                    postingPlanChange,
                    retryTemplate)
            );
        }


        executorService.shutdown();
        executorService.awaitTermination(20, TimeUnit.HOURS);

        Balance finalBalance = retryTemplate.execute(c -> serviceHandler.getBalanceByID(1L, Clock.latest(new LatestClock())));
        Assert.assertEquals(-ATTEMPTS, finalBalance.min_available_amount);
        Assert.assertEquals(ATTEMPTS , finalBalance.max_available_amount);
        Assert.assertEquals(0, finalBalance.own_amount);

    }
}
