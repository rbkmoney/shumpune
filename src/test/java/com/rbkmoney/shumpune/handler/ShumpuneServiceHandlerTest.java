package com.rbkmoney.shumpune.handler;

import com.rbkmoney.damsel.shumpune.*;
import com.rbkmoney.geck.common.util.TypeUtil;
import com.rbkmoney.shumpune.DaoTestBase;
import com.rbkmoney.shumpune.ShumpuneApplication;
import com.rbkmoney.shumpune.dao.AccountDao;
import com.rbkmoney.shumpune.dao.PlanDao;
import com.rbkmoney.shumpune.domain.BalanceModel;
import com.rbkmoney.shumpune.utils.AccountGenerator;
import com.rbkmoney.shumpune.utils.PostingGenerator;
import com.rbkmoney.shumpune.utils.VectorClockSerializer;
import org.apache.thrift.TException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShumpuneApplication.class)
public class ShumpuneServiceHandlerTest extends DaoTestBase {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AccountDao accountDao;

    @Autowired
    ShumpuneServiceHandler handler;

    @Autowired
    PlanDao planDao;

    @MockBean(name = "shumwayClient")
    AccounterSrv.Iface shumwayClient;

    @Test(expected = TException.class)
    public void holdAccountNotFountError() throws TException {
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange("plan_hold_error", 1111L, 21L, 22222L);
        handler.hold(postingPlanChange);
    }

    @Test
    public void hold() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planHold = "plan_hold";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planHold, providerAcc, systemAcc, merchantAcc);
        Clock clock = handler.hold(postingPlanChange);

        jdbcTemplate.query("select * from shm.posting_log where plan_id = \'" + postingPlanChange.getId() + "\'",
                (rs, rowNum) -> {
                    Assert.assertEquals(postingPlanChange.getBatch().getId(), rs.getLong("batch_id"));
                    Assert.assertTrue(rs.getLong("id") > 0);
                    return null;
                });

        checkMinAvailable(providerAcc, -300000L, merchantAcc, -9000L, systemAcc, -6000L, clock);
    }

    @Test(expected = TException.class)
    public void holdInvalidRequest() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planHold = "plan_hold_invalid_request";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planHold, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        //making plan incorrect
        postingPlanChange.getBatch().getPostings().get(0).setAmount(123L);
        handler.hold(postingPlanChange);
    }

    @Test(expected = TException.class)
    public void commitInvalidRequest() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planCommit = "plan_commit_invalid_postings";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planCommit, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId(planCommit)
                .setBatchList(batchList);

        handler.commitPlan(postingPlan);

        //making plan incorrect
        postingPlan.getBatchList().get(0).getPostings().get(0).setAmount(123L);
        handler.commitPlan(postingPlan);
    }

    @Test
    public void doubleHold() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planHold = "plan_hold_double";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planHold, providerAcc, systemAcc, merchantAcc);

        handler.hold(postingPlanChange);

        PostingPlan plan = handler.getPlan(planHold);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(3L, postingBatch.getPostings().size());
                });

        handler.hold(postingPlanChange);

        plan = handler.getPlan(planHold);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(3L, postingBatch.getPostings().size());
                });
    }

    private void checkMinAvailable(long providerAcc, long providerSum, long merchantAcc, long merchantSum,
                                   long systemAcc, long systemSum, Clock clock) {
        BalanceModel balance = planDao.getBalance(providerAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(providerSum, balance.getMinAvailableAmount().longValue());

        BalanceModel balanceMerch = planDao.getBalance(merchantAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(merchantSum, balanceMerch.getMinAvailableAmount().longValue());

        BalanceModel balanceSystem = planDao.getBalance(systemAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(systemSum, balanceSystem.getMinAvailableAmount().longValue());
    }

    @Test(expected = TException.class)
    public void commitError() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId("plan_commit_error")
                .setBatchList(batchList);

        handler.commitPlan(postingPlan);
    }

    @Test
    public void commit() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planCommit = "planCommit";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planCommit, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId(planCommit)
                .setBatchList(batchList);

        Clock clock = handler.commitPlan(postingPlan);

        checkMinAvailable(providerAcc, -294000L, merchantAcc, 291000L, systemAcc, 3000L, clock);
        checkOwnAcc(providerAcc, -294000L, merchantAcc, 291000L, systemAcc, 3000L, clock);

        PostingPlan plan = handler.getPlan(planCommit);

        Assert.assertEquals(planCommit, plan.getId());
        Assert.assertFalse(plan.getBatchList().isEmpty());
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(6L, postingBatch.getPostings().size());
                });
    }

    @Test
    public void doubleCommit() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planCommit = "plan_commit_double";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planCommit, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId(planCommit)
                .setBatchList(batchList);

        handler.commitPlan(postingPlan);

        PostingPlan plan = handler.getPlan(planCommit);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(6L, postingBatch.getPostings().size());
                });

        handler.commitPlan(postingPlan);

        plan = handler.getPlan(planCommit);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(6L, postingBatch.getPostings().size());
                });
    }

    @Test
    public void rollbackAfterCommit() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planCommit = "plan_rollback_after_commit";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planCommit, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId(planCommit)
                .setBatchList(batchList);

        handler.commitPlan(postingPlan);

        PostingPlan plan = handler.getPlan(planCommit);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(6L, postingBatch.getPostings().size());
                });

        handler.rollbackPlan(postingPlan);

        plan = handler.getPlan(planCommit);
        plan.getBatchList()
                .forEach(postingBatch -> {
                    Assert.assertEquals(PostingGenerator.BATCH_ID, postingBatch.getId());
                    Assert.assertEquals(6L, postingBatch.getPostings().size());
                });
    }

    private void checkOwnAcc(long providerAcc, long providerSum, long merchantAcc, long merchantSum,
                             long systemAcc, long systemSum, Clock clock) {
        BalanceModel balanceOwn = planDao.getBalance(providerAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(providerSum, balanceOwn.getOwnAmount().longValue());

        BalanceModel balanceMerchOwn = planDao.getBalance(merchantAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(merchantSum, balanceMerchOwn.getOwnAmount().longValue());

        BalanceModel balanceSystemOwn = planDao.getBalance(systemAcc, 0L, VectorClockSerializer.deserialize(clock.getVector()));
        Assert.assertEquals(systemSum, balanceSystemOwn.getOwnAmount().longValue());
    }

    private void checkAccs(long firstAcc, long firstAccOwnAmount, long firstAccMinAmount, long firstAccMaxAmount,
                           long secondAcc, long secondAccOwnAmount, long secondAccMinAmount, long secondAccMaxAmount,
                           long thirdAcc, long thirdAccOwnAmount, long thirdAccMinAmount, long thirdAccMaxAmount,
                           long fourthAcc, long fourthAccOwnAmount, long fourthAccMinAmount, long fourthAccMaxAmount,
                           Clock clock) throws TException {
        Balance balanceByID = handler.getBalanceByID(firstAcc, clock);
        Assert.assertEquals(firstAccOwnAmount, balanceByID.getOwnAmount());
        Assert.assertEquals(firstAccMinAmount, balanceByID.getMinAvailableAmount());
        Assert.assertEquals(firstAccMaxAmount, balanceByID.getMaxAvailableAmount());

        Balance balanceSecondOwn = handler.getBalanceByID(secondAcc, clock);
        Assert.assertEquals(secondAccOwnAmount, balanceSecondOwn.getOwnAmount());
        Assert.assertEquals(secondAccMinAmount, balanceSecondOwn.getMinAvailableAmount());
        Assert.assertEquals(secondAccMaxAmount, balanceSecondOwn.getMaxAvailableAmount());

        Balance balanceThirdOwn = handler.getBalanceByID(thirdAcc, clock);
        Assert.assertEquals(thirdAccOwnAmount, balanceThirdOwn.getOwnAmount());
        Assert.assertEquals(thirdAccMinAmount, balanceThirdOwn.getMinAvailableAmount());
        Assert.assertEquals(thirdAccMaxAmount, balanceThirdOwn.getMaxAvailableAmount());

        Balance balanceFourthOwn = handler.getBalanceByID(fourthAcc, clock);
        Assert.assertEquals(fourthAccOwnAmount, balanceFourthOwn.getOwnAmount());
        Assert.assertEquals(fourthAccMinAmount, balanceFourthOwn.getMinAvailableAmount());
        Assert.assertEquals(fourthAccMaxAmount, balanceFourthOwn.getMaxAvailableAmount());
    }

    @Test
    public void rollback() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long providerAcc = handler.createAccount(accountPrototype);
        long merchantAcc = handler.createAccount(accountPrototype);
        long systemAcc = handler.createAccount(accountPrototype);

        String planRollbaсk = "planRollbaсk";
        PostingPlanChange postingPlanChange = PostingGenerator.createPostingPlanChange(planRollbaсk, providerAcc, systemAcc, merchantAcc);
        handler.hold(postingPlanChange);

        PostingBatch batch = PostingGenerator.createBatch(providerAcc, systemAcc, merchantAcc);
        ArrayList<PostingBatch> batchList = new ArrayList<>();
        batchList.add(batch);
        PostingPlan postingPlan = new PostingPlan()
                .setId(planRollbaсk)
                .setBatchList(batchList);

        Clock clock = handler.rollbackPlan(postingPlan);


        checkMinAvailable(providerAcc, 0L, merchantAcc, 0L, systemAcc, 0L, clock);
        checkOwnAcc(providerAcc, 0L, merchantAcc, 0L, systemAcc, 0L, clock);
    }

    @Test
    public void createAccount() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long accountId = handler.createAccount(accountPrototype);
        Account account = handler.getAccountByID(accountId);
        assertAccount(account, accountPrototype);
        Assert.assertEquals(now.toString(), account.getCreationTime());

        //save without creation_time
        AccountPrototype accountPrototypeWithoutCreationTime = AccountGenerator.createAccountPrototype(null);
        accountId = handler.createAccount(accountPrototypeWithoutCreationTime);
        account = handler.getAccountByID(accountId);
        assertAccount(account, accountPrototype);
        Assert.assertTrue(TypeUtil.stringToInstant(account.getCreationTime()).isAfter(now));
    }

    @Test
    public void getBalanceCorrectForm() throws TException {
        Instant now = Instant.now();

        //simple save
        AccountPrototype accountPrototype = AccountGenerator.createAccountPrototype(now);
        long firstAcc = handler.createAccount(accountPrototype);
        long secondAcc = handler.createAccount(accountPrototype);
        long thirdAcc = handler.createAccount(accountPrototype);
        long fourthAcc = handler.createAccount(accountPrototype);

        PostingPlanChange postingPlanChange1 = PostingGenerator.createPostingPlanChange("1", firstAcc, secondAcc, thirdAcc, fourthAcc, 1);
        PostingPlanChange postingPlanChange2 = PostingGenerator.createPostingPlanChange("2", firstAcc, secondAcc, thirdAcc, fourthAcc, 3);
        PostingPlanChange postingPlanChange3 = PostingGenerator.createPostingPlanChange("3", firstAcc, secondAcc, thirdAcc, fourthAcc, 21);
        Clock clock = handler.hold(postingPlanChange1);
        checkAccs(firstAcc, 0, -6800, 80000,
                secondAcc, 0,  -1760, 2800,
                thirdAcc, 0, 0, 4000,
                fourthAcc, 0, -80000, 1760,
                clock);

        clock = handler.hold(postingPlanChange2);
        checkAccs(firstAcc, 0, -27200, 320000,
                secondAcc, 0,  -7040, 11200,
                thirdAcc, 0, 0, 16000,
                fourthAcc, 0, -320000, 7040,
                clock);
        clock = handler.hold(postingPlanChange3);
        checkAccs(firstAcc, 0, -170000, 2000000,
                secondAcc, 0,  -44000, 70000,
                thirdAcc, 0, 0, 100000,
                fourthAcc, 0, -2000000, 44000,
                clock);
        clock = handler.commitPlan(new PostingPlan("1", List.of(postingPlanChange1.getBatch())));
        checkAccs(firstAcc, 73200, -90000, 1993200,
                secondAcc, 1040,  -41200, 68240,
                thirdAcc, 4000, 4000, 100000,
                fourthAcc, -78240, -1998240, -36000,
                clock);

        clock = handler.commitPlan(new PostingPlan("2", List.of(postingPlanChange2.getBatch())));
        checkAccs(firstAcc, 292800, 150000, 1972800,
                secondAcc, 4160,  -32800, 62960,
                thirdAcc, 16000, 16000, 100000,
                fourthAcc, -312960, -1992960, -276000,
                clock);
        clock = handler.rollbackPlan(new PostingPlan("3", List.of(postingPlanChange3.getBatch())));
        checkAccs(firstAcc, 292800, 292800,292800,
                secondAcc, 4160, 4160,4160,
                thirdAcc, 16000, 16000,16000,
                fourthAcc, -312960, -312960,-312960,
                clock);

        Balance balance = handler.getBalanceByID(firstAcc, Clock.latest(new LatestClock()));
        Balance balanceSecond = handler.getBalanceByID(firstAcc, Clock.latest(new LatestClock()));

        Assert.assertEquals(balance.getClock(), balanceSecond.getClock());

        Balance unknownAccount = handler.getBalanceByID(12321L, Clock.latest(new LatestClock()));

        Assert.assertEquals(0L, unknownAccount.getOwnAmount());
    }

    private void assertAccount(Account account, AccountPrototype accountPrototype) {
        Assert.assertEquals(accountPrototype.getCurrencySymCode(), account.getCurrencySymCode());
        Assert.assertEquals(accountPrototype.getDescription(), account.getDescription());
    }

}