# Twitch Chat Viewer

[![Java CI](https://github.com/k7t3/TwitchChatViewer/actions/workflows/test.yaml/badge.svg)](https://github.com/k7t3/TwitchChatViewer/actions/workflows/test.yaml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

このアプリケーションはライブストリーミングサービス[Twitch](https://www.twitch.tv/)の非公式チャットビューアです。  
チャットを快適に視聴することを目的にしています！

!["メインビュー"](docs/images/01.main.png)

## 主な機能

### マルチチャンネルのチャット閲覧
* 複数のチャンネルのチャットを同時に楽しめます。

### チャットのマージ
* 任意のチャットをマージして一つのビューアに集約できます。

### チャットビューアのポップアウト
* チャットビューアを透過ウインドウとしてポップアウトできます。
* 常に最前面に表示するオプションを使用して作業領域を妨げません。

!["チャットビューのポップアウト"](docs/images/02.popout.png)

### チャットに投稿されたクリップの検出
* 表示しているチャットに投稿されたクリップを検出できます。
* チャットに投稿されたクリップを見逃しません。

!["チャットに投稿されたクリップ"](docs/images/03.clips.png)

### カスタマイズ可能なチャットビューア
* フォントの変更やユーザー名・バッジの表示切り替えなど、チャットビューアをカスタマイズできます。
* 表示領域を最大限有効活用できます。

### チャットメッセージのフィルタリング
* 特定のユーザーのメッセージを非表示にしたり、正規表現を使用してフィルタリングできます。

## コアライブラリ

* [Twitch4J](https://twitch4j.github.io/) - Twitch APIのクライアント
* [JavaFX](https://openjfx.io/) - UIコンポーネント
* [AtlantaFX](https://mkpaz.github.io/atlantafx/) - モダンで美しいUIテーマ

### Icon

[FLAT ICON DESIGN](http://flat-icon-design.com/)

## License

詳細は [LICENSE](LICENSE.md) を参照してください。