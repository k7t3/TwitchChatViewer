# Twitch Chat Viewer

[![Java CI](https://github.com/k7t3/TwitchChatViewer/actions/workflows/test.yaml/badge.svg)](https://github.com/k7t3/TwitchChatViewer/actions/workflows/test.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

このアプリケーションはライブストリーミングサービス[Twitch](https://www.twitch.tv/)の非公式チャットビューアです。

<img src="docs/images/04アプリケーション画面.png" width="1024" alt="アプリケーション画面">

[使用方法](docs/usage.md)

## 主な機能

### マルチチャンネルのチャット閲覧
* 複数のチャンネルのチャットを同時に視聴できます。

### チャットのマージ
* 任意のチャットをマージして一つのビューアに集約できます。
  <img src="docs/images/06マージされた.png" width="640" alt="マージされたチャット">

### チャットビューアのポップアウト
* チャットビューアを透過ウインドウとしてポップアウトして最前面に配置できます。
* 配信画面や作業ウインドウの任意の場所に配置してスペースを有効活用しましょう。

<img src="docs/images/09ポップアウトウィンドウ.png"  alt="ポップアウト">

### チャットに投稿されたクリップの検出
* 表示しているチャットに投稿されたクリップを検出できます。
* 投稿された回数を記録しており、ホットなクリップがひと目で確認できます。

<img src="docs/images/08クリップ.png" width="1024" alt="クリップ">

### チャンネルのグループ管理
* チャンネルを任意のグループに登録することができます。
<img src="docs/images/07チャンネルグループ.png" width="1024" alt="チャンネルグループ">

### カスタマイズ可能なチャットビューア
* フォントの変更やユーザー名・バッジの表示切り替えなど、チャットビューアをカスタマイズできます。
* 表示領域を最大限有効活用できます。

### チャットメッセージのフィルタリング
* 特定のユーザーのメッセージを非表示にしたり、正規表現を使用してフィルタリングできます。

## License

詳細は [LICENSE](LICENSE.md) を参照してください。