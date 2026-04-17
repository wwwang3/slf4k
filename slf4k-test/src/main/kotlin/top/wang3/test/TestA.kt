package top.wang3.test

import top.wang3.slf4k.Slf4k

@Slf4k
class TestA {

    fun greet(name: String) {
        logger.info("hello {}", name)
    }
}
