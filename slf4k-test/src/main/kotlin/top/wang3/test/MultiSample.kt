package top.wang3.test

import top.wang3.slf4k.Slf4k

@Slf4k
class MultiA {
    fun a() {
        logger.info("MultiA.a")
    }
}

@Slf4k
class MultiB {
    fun b() {
        logger.info("MultiB.b")
    }
}
