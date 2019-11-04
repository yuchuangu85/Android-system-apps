package com.android.documentsui.testing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Helper class for testing async processes.
 */
public class LatchedConsumer<T> implements Consumer<T> {

    private T value;
    private CountDownLatch latch;

    public LatchedConsumer(int expectedCount) {
        latch = new CountDownLatch(expectedCount);
    }

    public CountDownLatch getLatch() { return latch; }
    public T getValue() { return value; }


    @Override
    public void accept(T value) {
        this.value = value;
        latch.countDown();
    }

    public void assertNotCalled(long timeout, TimeUnit unit)
            throws InterruptedException {
        assertFalse(latch.await(timeout, unit));
    }

    public void assertCalled(long timeout, TimeUnit unit)
            throws InterruptedException {
        assertTrue(latch.await(timeout, unit));
    }
}
