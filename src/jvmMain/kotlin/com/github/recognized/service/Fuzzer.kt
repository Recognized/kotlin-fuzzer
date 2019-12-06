package com.github.recognized.service

import com.github.recognized.Server

class FuzzerImpl : Fuzzer {

    override suspend fun stat(): Statistics {
        return Server.stat()
    }

    override suspend fun start() {
        Server.start()
    }
}