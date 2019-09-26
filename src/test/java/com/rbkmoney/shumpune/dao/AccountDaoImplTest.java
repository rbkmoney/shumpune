package com.rbkmoney.shumpune.dao;

import com.rbkmoney.damsel.shumpune.Account;
import com.rbkmoney.shumpune.DaoTestBase;
import com.rbkmoney.shumpune.ShumpuneApplication;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Instant;
import java.util.List;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ShumpuneApplication.class)
public class AccountDaoImplTest extends DaoTestBase {

    @Autowired
    AccountDaoImpl accountDaoImpl;

    @Test
    public void batchAccountInsertTest() {
        accountDaoImpl.batchAccountInsert(List.of(
                new Account(1L, "RUB").setCreationTime(Instant.now().toString()).setDescription("1"),
                new Account(1L, "RUB").setCreationTime(Instant.now().toString()).setDescription("1"),
                new Account(2L, "RUB").setCreationTime(Instant.now().toString()).setDescription("2"),
                new Account(3L, "RUB").setCreationTime(Instant.now().toString()).setDescription("3"),
                new Account(4L, "RUB").setCreationTime(Instant.now().toString()).setDescription("4")
        ));

        Assert.assertTrue(accountDaoImpl.getAccountById(1L).isPresent());
        Assert.assertTrue(accountDaoImpl.getAccountById(2L).isPresent());
        Assert.assertTrue(accountDaoImpl.getAccountById(3L).isPresent());
        Assert.assertTrue(accountDaoImpl.getAccountById(4L).isPresent());
        Assert.assertFalse(accountDaoImpl.getAccountById(5L).isPresent());
    }
}