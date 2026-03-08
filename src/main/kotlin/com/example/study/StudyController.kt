package com.example.study

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration

@RestController
class StudyController {

    private val log = LoggerFactory.getLogger(javaClass)

    // ----------------------------------------------------------------
    // 1. 非ブロッキング（正しい使い方）
    //    Reactor のスレッドをブロックせず、遅延は delay で表現
    // ----------------------------------------------------------------
    @GetMapping("/non-blocking")
    fun nonBlocking(): Mono<String> {
        log.info("[non-blocking] start  thread={}", currentThread())
        return Mono.delay(Duration.ofSeconds(1))
            .map {
                log.info("[non-blocking] finish thread={}", currentThread())
                "non-blocking: OK"
            }
    }

    // ----------------------------------------------------------------
    // 2. ブロッキング（NG パターン）
    //    Reactor スレッド上で Thread.sleep を呼び、スレッドを占有する
    //    → 他のリクエストが処理できなくなる
    // ----------------------------------------------------------------
    @GetMapping("/blocking-bad")
    fun blockingBad(): Mono<String> {
        log.info("[blocking-bad] start  thread={}", currentThread())
        // TODO: ここを試してみよう
        //   - 並列で複数リクエストを投げると詰まる様子が観察できる
        Thread.sleep(1_000)
        log.info("[blocking-bad] finish thread={}", currentThread())
        return Mono.just("blocking-bad: OK (but this is bad!)")
    }

    // ----------------------------------------------------------------
    // 3. ブロッキング処理を boundedElastic でラップ（OK パターン）
    //    ブロッキング処理は専用スレッドプールで実行する
    // ----------------------------------------------------------------
    @GetMapping("/blocking-good")
    fun blockingGood(): Mono<String> {
        log.info("[blocking-good] start  thread={}", currentThread())
        return Mono.fromCallable {
            // このラムダは boundedElastic スレッドで動く
            log.info("[blocking-good] blocking thread={}", currentThread())
            Thread.sleep(1_000)   // ← ブロッキング処理（DB/外部API 呼び出しを想定）
            "blocking-good: OK"
        }
            .subscribeOn(Schedulers.boundedElastic())
            .doOnSuccess {
                log.info("[blocking-good] finish thread={}", currentThread())
            }
    }

    // ----------------------------------------------------------------
    // 4. Flux でのストリーミング（非ブロッキング）
    //    1秒ごとに値を流す
    // ----------------------------------------------------------------
    @GetMapping("/flux-stream", produces = ["text/event-stream"])
    fun fluxStream(): Flux<String> {
        return Flux.interval(Duration.ofSeconds(1))
            .take(5)
            .map { i ->
                log.info("[flux-stream] emit i={} thread={}", i, currentThread())
                "event-$i"
            }
    }

    // ----------------------------------------------------------------
    // 5. Flux 内でブロッキング（NG パターン）
    //    TODO: どうなるか試してみよう
    //    → ブロッキング処理が Reactor スレッドで走り、全体が詰まる
    // ----------------------------------------------------------------
    @GetMapping("/flux-blocking-bad")
    fun fluxBlockingBad(): Flux<String> {
        return Flux.range(1, 3)
            .map { i ->
                log.info("[flux-blocking-bad] before sleep i={} thread={}", i, currentThread())
                Thread.sleep(500)   // ← NG: Reactor スレッドをブロック
                log.info("[flux-blocking-bad] after  sleep i={} thread={}", i, currentThread())
                "item-$i"
            }
    }

    // ----------------------------------------------------------------
    // 6. Flux 内ブロッキングを flatMap + boundedElastic で修正（OK パターン）
    //    TODO: /flux-blocking-bad と比較してみよう
    // ----------------------------------------------------------------
    @GetMapping("/flux-blocking-good")
    fun fluxBlockingGood(): Flux<String> {
        return Flux.range(1, 3)
            .flatMap { i ->
                Mono.fromCallable {
                    log.info("[flux-blocking-good] blocking i={} thread={}", i, currentThread())
                    Thread.sleep(500)
                    "item-$i"
                }.subscribeOn(Schedulers.boundedElastic())
            }
    }

    // ----------------------------------------------------------------
    // ユーティリティ
    // ----------------------------------------------------------------
    private fun currentThread() = Thread.currentThread().name
}
