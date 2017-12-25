# akka-stability-patterns

[Akkaが実現する安定性のパターン - Qiita](https://qiita.com/negokaz/items/ba065abcb5ee40c150a9)のサンプルコードです。

以下に、各パターンの動きを確認するための方法を記載します。

## タイムタウト

```bash
sbt future-timeout

Future(Failure(java.util.concurrent.TimeoutException))
```
[実装コード](./src/main/scala/io/github/negokaz/FutureTimeoutApp.scala)

"hello" が返ってくるのに 1000 ms かかるのに対し、タイムタウトが 200 ms なので `Future` は `Failure` になる。

## サーキットブレーカー

ターミナルを4つ使います。

### 1. DBを起動（ターミナル1）

```bash
sbt h2-server
```

### 2. ユーザー登録サービスを起動（ターミナル2）

```bash
sbt user-registry-service
```

### 3. ユーザークエリサービスを起動（ターミナル3）

```bash
sbt user-query-service
```

### 4. ユーザーを登録（ターミナル4）

```bash
curl -X POST -H 'Content-Type: application/json' -d '{"name": "hoge"}' http://localhost:8080/users

{"description":"User hoge created."}
```

### 5. ユーザー一覧をユーザークエリサービスから取得（ターミナル4）

ユーザークエリサービスが正常に稼働していることを確認する

```bash
curl -X GET http://localhost:8081/userlist

[{"id":1,"name":"hoge"}]
```

### 6. ユーザー登録サービスを高負荷にする（ターミナル2）

`Ctrl + C` でユーザー登録サービスを一旦停止して、応答速度の遅いユーザー登録サービスを立ち上げる（高負荷をシュミレート）。

```
sbt slow-user-registry-service
```

### 7. ユーザー一覧をユーザークエリサービスから取得（ターミナル4）

```bash
curl -X GET http://localhost:8081/userlist

There was an internal server error.
```

ユーザークエリサービスからユーザー登録サービスへの問い合わせがタイムタウトする。
はじめはエラーレスポンスが返ってくるのに数秒かかるが、何度か上記のコマンドを実行するとサーキットブレーカーが効いてすぐにエラーレスポンスが返ってくるようになる。

## Let it Crash

ターミナルを3つ使います。

### 1. DBを起動（ターミナル1）

```bash
sbt h2-server
```

### 2. ユーザー登録サービスを起動（ターミナル2）

```bash
sbt user-registry-service
```

### 3. ユーザーを登録（ターミナル3）

```bash
curl -X POST -H 'Content-Type: application/json' -d '{"name": "fuga"}' http://localhost:8080/users

{"description":"User fuga created."}
```

### 4. ユーザー一覧を取得（ターミナル3）

```bash
curl -X GET http://localhost:8080/users

[{"id":1,"name":"fuga"}]
```

### 5. `Ctrl + C` で DB をシャットダウン（ターミナル1）

DBとの接続障害をシュミレートする

### 6. ユーザー一覧を取得（ターミナル3）

```bash
curl -X GET http://localhost:8080/users

There was an internal server error.
```

DB との接続が切れたことでユーザー登録サービスで例外が発生。ログで `UserRegistryActor` が再起動したことがわかる。（ターミナル2）

```
org.h2.jdbc.JdbcSQLException: 接続が壊れています: "java.net.ConnectException: Connection refused: localhost"
Connection is broken: "java.net.ConnectException: Connection refused: localhost" [90067-196]
	at org.h2.message.DbException.getJdbcSQLException(DbException.java:345)
Caused by: java.net.ConnectException: Connection refused
	at java.net.PlainSocketImpl.socketConnect(Native Method)
[ERROR] [i.g.n.UserRegistryActor] Stopped
[WARN ] [i.g.n.UserRegistryActor] Restarted     ← UserRegistryActor が再起動した
```

### 7. DBを再起動（ターミナル1）

```bash
sbt h2-server
```
### 8. ユーザー一覧を取得（ターミナル3）

障害から回復できていることが確認できる。

```bash
curl -X GET http://localhost:8080/users

[{"id":1,"name":"fuga"}]
```
