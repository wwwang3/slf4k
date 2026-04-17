package top.wang3.test

import top.wang3.slf4k.Slf4k

@Slf4k
enum class EnumSample {
    A,
    B;

    fun announce() {
        logger.info("enum entry {}", name)
    }
}
