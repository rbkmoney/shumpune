package com.rbkmoney.shumpune.utils;

import com.rbkmoney.shumpune.exception.DaoException;

public class DaoUtils {

    public static void checkBatchUpdate(int[][] updateCounts) {
        boolean checked = false;
        for (int[] updateCount : updateCounts) {
            for (int i : updateCount) {
                checked = true;
                if (i != 1) {
                    throw new DaoException("Unexpected update count: " + i);
                }
            }
        }
        if (!checked) {
            throw new DaoException("Unexpected update count [0]");
        }
    }

}
