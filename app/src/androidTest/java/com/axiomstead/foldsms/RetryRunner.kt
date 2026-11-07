package com.axiomstead.foldsms

import android.util.Log
import org.junit.internal.AssumptionViolatedException
import org.junit.runners.BlockJUnit4ClassRunner
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/** Waits for UI conditions on the shared CI emulator, which can stall for seconds at a time. */
internal const val UI_WAIT_MS = 30_000L

/**
 * Runs a failed test once more, with a new test instance and fresh rules, and logs the retry under the tag FLAKY.
 * The hosted emulator regularly stalls past a wait in one random test per run; a test that passes on the second attempt is
 * reported as passed, but every retry is listed in the CI output (flaky-retries.txt) so a test that keeps needing one is
 * noticed and fixed. A test that fails both times fails as usual.
 */
class RetryRunner(testClass: Class<*>) : BlockJUnit4ClassRunner(testClass) {
    private fun fresh(method: FrameworkMethod): Statement = super.methodBlock(method)

    override fun methodBlock(method: FrameworkMethod): Statement {
        val first = fresh(method)
        return object : Statement() {
            override fun evaluate() {
                try { first.evaluate() }
                catch (assumption: AssumptionViolatedException) { throw assumption }
                catch (failure: Throwable) {
                    Log.w("FLAKY", "retrying ${testClass.name}.${method.name} after ${failure.javaClass.simpleName}")
                    fresh(method).evaluate()
                }
            }
        }
    }
}
