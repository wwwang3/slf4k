package top.wang3.test

import top.wang3.slf4k.Slf4k

class OuterSample {

    @Slf4k
    class Nested {
        fun ping() {
            logger.info("nested ping")
        }
    }
}
