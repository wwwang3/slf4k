package top.wang3.test

import top.wang3.slf4k.Slf4k

@Slf4k
class TestB {

    fun doWork() {
        logger.warn("doing work in TestB")
    }

    companion object {
        const val NAME: String = "TestB"
    }
}
