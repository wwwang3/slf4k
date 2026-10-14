package top.wang3.test

import top.wang3.slf4k.Slf4k

@Slf4k
object ObjectDemo {

    fun greet(name: String) {
        logger.info("hello {}", name)
    }
}
