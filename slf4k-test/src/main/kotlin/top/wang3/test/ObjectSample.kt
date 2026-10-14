package top.wang3.test

import top.wang3.slf4k.Slf4k

class ObjectHolder {

    @Slf4k
    object Nested {
        fun ping() {
            logger.info("nested object ping")
        }
    }
}

@Slf4k
object OuterObject {

    @Slf4k
    object Inner {
        fun ping() {
            logger.info("inner object ping")
        }
    }
}

@Slf4k
data object DataObjectSample {
    fun announce() {
        logger.info("data object announce")
    }
}
