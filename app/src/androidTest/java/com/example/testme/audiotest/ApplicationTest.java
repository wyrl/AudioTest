package com.example.testme.audiotest;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

/**
 * Basic smoke test for the app test environment.
 */
@RunWith(AndroidJUnit4.class)
public class ApplicationTest {
    @Test
    public void applicationContext_isAvailable() {
        assertNotNull(ApplicationProvider.getApplicationContext());
    }
}
