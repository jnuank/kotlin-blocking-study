# Kotlin Spring Boot Flux - ブロッキング検証サンプル

## 起動

```bash
./gradlew bootRun
```

## エンドポイント一覧

| パス | 説明 | ブロッキング？ |
|------|------|---------------|
| `GET /non-blocking` | `Mono.delay` で非同期に遅延 | NG |
| `GET /blocking-bad` | Reactor スレッドで `Thread.sleep` | **NG** |
| `GET /blocking-good` | `boundedElastic` スレッドで `Thread.sleep` | OK |
| `GET /flux-stream` | 1秒ごとに SSE でイベントを流す | NG |
| `GET /flux-blocking-bad` | `Flux.map` 内で `Thread.sleep` | **NG** |
| `GET /flux-blocking-good` | `flatMap + boundedElastic` でラップ | OK |

## 試し方

### 1. 基本動作確認

```bash
curl http://localhost:8080/non-blocking
curl http://localhost:8080/blocking-bad
curl http://localhost:8080/blocking-good
```

### 2. ブロッキングの影響を観察する

ターミナルを2つ開いて並列リクエストを投げる:

```bash
# ターミナル1
curl http://localhost:8080/blocking-bad &
curl http://localhost:8080/blocking-bad &
curl http://localhost:8080/blocking-bad &
wait

# ターミナル2（別ウィンドウで同時に）
curl http://localhost:8080/non-blocking
```

`blocking-bad` が Reactor スレッドを占有して `/non-blocking` の応答が遅れる様子を確認できる。

### 3. SSE ストリームを観察する

```bash
curl -N http://localhost:8080/flux-stream
```

### 4. Netty スレッド数を制限してさらに観察

`application.properties` の以下のコメントを外す:

```properties
server.netty.threads=2
```

スレッドが2本しかないため、`/blocking-bad` への並列リクエストで詰まりやすくなる。

## TODO: 試してほしいこと

- [ ] `/blocking-bad` に並列で10リクエスト飛ばして `/non-blocking` の応答時間を計測する
- [ ] `server.netty.threads=2` にしてブロッキングの影響を拡大させる
- [ ] BlockHound を有効化してブロッキング呼び出しを自動検出させる
- [ ] `Schedulers.parallel()` と `Schedulers.boundedElastic()` を使い分ける
- [ ] `flatMap` の並列数 (`concurrency` 引数) を変えてみる
