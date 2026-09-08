# 栞・既読位置サーバー同期 API仕様

このフォークの「栞同期の設定」機能は、複数端末間で以下の2つを共有します。

- **栞(bookmarks)**: 明示的に「ブックマーク追加」した名前付きマーカー。1ファイルに複数件あってよい。
- **既読位置(read_position)**: ページを閉じるたびに自動で保存される「現在の読書位置」。1ファイルにつき常に1件のみ。

対象は**SMBサーバー上のファイルのみ**(端末ローカルのファイルは同期対象外)。

この機能はアプリ単体では動作せず、以下のAPI仕様を満たす**REST APIサーバーを別途自分で用意して自己ホストする**必要があります。EverythingXなど他の連携機能とは無関係・無依存です。参照実装(Flask + SQLite)は非公開ですが、以下の仕様を満たせば任意の言語・フレームワークで自作したサーバーでも利用できます。

## クライアント側の設定

アプリの「設定 > その他の設定 > 栞同期の設定」で、サーバーのHost/Port/User/Passを入力します(User/Passは任意、HTTP Basic認証を使う場合のみ)。この1箇所の設定で栞・既読位置の両方の同期に使われます。

## エンドポイント一覧

| メソッド | パス | 用途 |
|---|---|---|
| GET | `/bookmarks?host=<host>` | 指定ホストの栞一覧を取得(`host`省略時は全件) |
| POST | `/bookmarks` | 栞を1件upsert |
| DELETE | `/bookmarks` | 栞を1件削除 |
| GET | `/read_position?host=<host>&path=<path>&file=<file>` | 指定ファイルの既読位置を1件取得(無ければ404) |
| POST | `/read_position` | 既読位置を1件upsert |
| GET | `/health` | 疎通確認(任意) |

### GET /bookmarks

指定ホスト(省略時は全ホスト)の栞一覧をJSON配列で返す。

```
GET /bookmarks?host=100.122.74.51
```

レスポンス例:

```json
[
  {
    "host": "100.122.74.51",
    "path": "/Manga/SeriesName/",
    "file": "vol1.zip",
    "page": 42,
    "image": "00042.jpg",
    "chapter": -1,
    "pagerate": -1,
    "dispname": "お気に入りシーン",
    "type": 0,
    "date": 1735689600
  }
]
```

### POST /bookmarks

栞を1件upsertする。主キーは `(host, path, file, page)` — 同じ組み合わせで再度POSTすると上書きされる。

リクエストボディ(JSON、`host`/`path`/`file`/`page`は必須):

```json
{
  "host": "100.122.74.51",
  "path": "/Manga/SeriesName/",
  "file": "vol1.zip",
  "page": 42,
  "image": "00042.jpg",
  "chapter": -1,
  "pagerate": -1,
  "dispname": "お気に入りシーン",
  "type": 0,
  "date": 1735689600
}
```

成功時は `{"status": "ok"}` などのJSONを返せばよい(クライアントはHTTP 200のみ確認し、ボディの内容は特に見ない)。

### DELETE /bookmarks

栞を1件削除する。リクエストボディ(JSON): `{"host", "path", "file", "page"}`。

### GET /read_position

指定ファイル1件の既読位置を返す。`host`/`file`は必須(`path`は空でもよい)。

```
GET /read_position?host=100.122.74.51&path=/Manga/SeriesName/&file=vol1
```

- 見つかった場合: HTTP 200、以下の形式のJSONを1件返す。
- 見つからない場合: HTTP 404。

```json
{
  "host": "100.122.74.51",
  "path": "/Manga/SeriesName/",
  "file": "vol1",
  "page": 22,
  "maxpage": 40,
  "chapter": -1,
  "pagerate": -1,
  "date": 1735689600
}
```

**注意**: クライアントは`file`を拡張子を除いた状態で送信する(ZIP/RARなど拡張子違いの同一ファイルを同一視するため)。サーバー側は受け取った文字列をそのままキーとして扱えばよく、拡張子の処理は不要。

### POST /read_position

既読位置を1件upsertする。主キーは `(host, path, file)`(`page`は主キーに含まない = 1ファイルにつき常に最新の1件のみ保持)。`host`/`path`/`file`は必須。

リクエストボディ(JSON):

```json
{
  "host": "100.122.74.51",
  "path": "/Manga/SeriesName/",
  "file": "vol1",
  "page": 22,
  "maxpage": 40,
  "chapter": -1,
  "pagerate": -1,
  "date": 1735689600
}
```

## 認証

「栞同期の設定」にUser/Passを入力した場合、クライアントは全リクエストにHTTP Basic認証ヘッダーを付与する。サーバー側で認証を必須にするかどうかは任意(Tailscale等の閉域網内運用であれば無認証でもよい)。

## 通信仕様上の注意

- クライアントは既読位置の取得(GET /read_position)を**ファイルを開く直前に同期的に**行い、サーバー側に値があればローカルの値より優先して復元する。タイムアウトは1.5秒程度を想定しているため、サーバーは低レイテンシで応答できることが望ましい。
- 既読位置の送信(POST /read_position)は、ページ送りのたびではなく、ビューアの一時停止または明示的に閉じるタイミングでベストエフォート送信される。送信失敗時のリトライは行われない。
- 栞の一覧取得(GET /bookmarks)は、栞タブを開くたびに実行され、サーバー側の内容で該当ホスト分のローカル表示を置き換える(サーバーを正とするモデル)。
