# ComittoNxX修正版

<img src="app/src/main/res/drawable-hdpi/comittonxx.png" width="100" align="right" alt="logo">

ComittoNxX はオープンソースの画像ビュワーです.  
対応バージョンは Android7.0 以上 17 までです.

- 対応する電子書籍ファイルの形式：  
  PDF、EPUB、TEXT、青空文庫

- 対応する画像ファイルの形式：  
  JPEG、GIF、PNG、WebP、AVIF、HEIF(Android 8.0以上)、JXL

- 対応する圧縮ファイルの形式：  
  ZIP、RAR

- 対応する共有オンラインストレージサービス：
  - Windowsファイル共有(SMB)
  - 外部アプリを介した共有オンラインストレージへのアクセス  
    (一応動作はしますが、組み合わせによっては使い物になりません)
    - ストレージアクセスフレームワーク(SAF)
      - CIFS Documents Provider : [Google Play](https://play.google.com/store/apps/details?id=com.wa2c.android.cifsdocumentsprovider)、[マニュアル](https://github.com/wa2c/cifs-documents-provider/wiki/Manual-ja)  
        SMB、FTP、FTPS、SFTPをサポート.
      - Round Sync : [GitHub](https://github.com/newhinton/Round-Sync)  
        多くのクラウド ストレージ プロバイダーに対応.
    - ファイルピッカー
      - Google ドライブ
      - Microsoft OneDrive
      - その他メーカー純正アプリ等

    アカウントの認証などは外部アプリ側で設定してください.  
    これらの外部アプリは起動時に自動実行やバックグラウンドで停止しないなどの設定を推奨します.

<!-- 
> [!IMPORTANT]
> 更新を再開しました.
-->

<!-- 
> [!NOTE]
> 掲示板への投稿が規制されたためバージョン更新の告知を停止しています.
-->

## 注意事項

- 32bit版アプリや32bitOSを使用している場合は使用メモリサイズを少なめにしてください.
- SMBの接続情報にドメイン名を入力すると初回接続が遅くなる場合があります.  不要なドメイン名(WORKGROUP等)の使用は避けてください.
- オンラインストレージ上のRAR圧縮形式ファイルの構造解析は時間がかかります.サムネイル作成時にファイル名を解析する場合にはZIP圧縮形式の使用を推奨します.

## 既知の不具合

- アプリ内ヘルプの内容が古いです.  
- Android 10 でファイルのダウンロードができません.  
- イメージビュワー閲覧中にピンチイン・ピンチアウトでイメージを拡大中に画像の表示位置が乱れます.  
- イメージビュワー閲覧中にルーペ表示の拡大率が元画像サイズの〇倍のときに画像の表示位置が乱れます.  

## ダウンロード

[Releases](https://github.com/comittonxxa2/ComittoNxX/releases/latest) よりご利用ください.

#### [NxD](https://github.com/Kdroidwin/cnxd/tree/cnxd) からの修正点

- PNG形式で表示できないファイルがあったのを修正.
- AVIF形式画像の表示に対応.
- HEIF形式画像の表示に対応.(Android 8.0以上)
- JXL形式画像の表示に対応.
- PDF形式電子書籍の表示に対応.
- EPUB形式電子書籍の本文テキストの表示に対応.

#### このフォーク(full-local-features)での追加修正点

- Everything(Windows)/EverythingXによるファイル検索機能を追加.  
  ファイル一覧下部に専用の「検索」タブを新設し、SMBパスへの変換を行った上で検索結果から直接ファイル/フォルダを開けます.
- 栞・既読位置・履歴・書庫カタログを複数端末間で共有する機能を追加.  
  別途自己ホストするREST APIサーバー経由で、栞(ブックマーク)の追加/削除、自動保存される既読ページ位置、最近開いたファイル(履歴)、SMB共有上のアーカイブ一覧(書庫カタログ)を端末間で同期します.SMB上のファイルのみ対象.サーバーの実装(非公開)はAPI仕様を満たせば自作でも構いません.仕様(栞/既読位置/履歴/書庫管理すべて含む)は [docs/bookmark-sync-api.md](docs/bookmark-sync-api.md) を参照してください.
- 上記の同期サーバー上に構築した書庫カタログを閲覧する「書庫管理」タブを追加.  
  作品単位でまとめて一覧表示し、巻ごとの既読状況(未読/既読率/既読)をタイトル色分け付きで表示、作品を跨がず巻単位での「次/前ファイル」移動に対応.
- ZIP⇔RARなど拡張子違い・ディレクトリ移動後でも既読履歴/栞を引き継げるようにファイル名フォールバック機能を追加.(画像/アーカイブビューアのみ)
- ファイル一覧の「マーカー設定」に「ファイルのみ表示」「フォルダのみ表示」の切り替えを追加.
- 既読位置がサーバー管理になったことに伴い、設定のエクスポート/インポート機能からSMB上のファイルの既読位置キャッシュを除外するよう修正.
- SMB接続ライブラリのSingletonContext初期化がバックグラウンド経路によって競合し、チューニング設定が反映されないことがある不具合を修正.
- ファイル一覧表示時の既読位置検索処理を事前インデックス化し、ファイル数が多いフォルダでの表示を高速化.
- 7-Zip-JBindingでアーカイブを開いた際、ネイティブハンドルのみ解放されストリーム(SMB等のファイルハンドル)が解放されずに残ってしまう不具合を修正.(本家にはまだ未反映)

#### 本家(comittonxxa2/ComittoNxX)との差異

このフォークは2026-10-24に本家バージョン `2.4.10.CN` までを取り込み済みです(以降の本家更新は未反映).取り込みにあたり、本家側で追加されていた以下の機能も利用可能になっています.

- WebDAVアクセス対応(SMB/SAF/ファイルピッカーに加える新しいアクセス方式).
- 端末内にWebサーバーを立てて同じネットワーク上のPC等のブラウザからファイルを閲覧・ダウンロード・アップロード・管理できる「Web共有」機能.
- 栞/既読位置/履歴/書庫管理それぞれの同期機能・Everything検索について、設定画面に有効/無効の切り替えと接続先の入力検証を追加.
- Everything検索のクエリエンコード・文字コード・Windowsパス区切り文字対応の修正.

一方で、上記「追加修正点」のうち書庫管理タブの既読状況表示・巻単位の次/前ファイル移動、既読位置検索の高速化、SMBハンドル解放の修正は、本家にはまだ反映されていないこのフォーク独自の変更です.

## ビルド

アプリをビルドするには [Git](https://git-scm.com/)、[Ninja](https://ninja-build.org/)、[Meson](https://mesonbuild.com/)、[pkg-config](https://www.freedesktop.org/wiki/Software/pkg-config/)、[NASM](https://www.nasm.us/) のインストールが必要です.  
[Android Studio](https://developer.android.com/studio/install) を利用するか、gradlewコマンドでアプリをビルドしてください.

<details><summary><b>ビルドに必要なファイルの作成手順</b></summary>
<p>

###### 署名の作成

キーストアファイルを作成して保存します.  
Android studio の場合は [Build] > [Generate Signed Bundle/APK] から作成します.

###### signingConfigs/release.gradle の作成

プロジェクトルートに signingConfigs というフォルダを作成します.  
signingConfigs の中に release.gradle というファイルを作成します.

```gradle
signingConfigs {
    release {
        storePassword '${署名ファイルのパスワード}'
        keyPassword '${鍵のパスワード}'
        storeFile file('${署名のファイル名}')
        keyAlias '${鍵のエイリアス}'
    }
}
```
</details>

## ライセンス

LICENSE ファイルに記載されたライセンスに基づきます.  
内部で使用しているライブラリのライセンスは各ライブラリの指定に従います.  

本ソースは、[ComittoNおよびComittoNxN](https://docs.google.com/open?id=0Bzx6UxEo3Pg0SXNIQVdRVnVqemM)、[ComittoNxM](https://www.axfc.net/u/3792235)、[ComittoNxT](https://www.axfc.net/u/3978158)、[ComittoNxA](https://github.com/ComittoNxA/ComittoNxA/tree/1.65A20)、[ComittoNxAC](https://www.axfc.net/u/4059552)、[ComittoNxD](https://github.com/Kdroidwin/cnxd/tree/cnxd) のソースを元にしています.  
