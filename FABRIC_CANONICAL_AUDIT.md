# Fabric Canonical仕様: 最終残存差分監査

## 監査基準

今回の再監査は、古い `e289d16...` ではなく、変更前の最新 `origin/fabric` `d056439eb6479d42383f8655d1a65a792933ab54` を基準に実施した。`origin/main` の変更前基準は `b738a876145e69c08d180ff561a2de13f2a598a9` である。既存のdirty worktreeは変更していない。

## 今回解消した残存差分

- 1.16.5: Java 8互換のOS fallbackで `tasklist` の終了コードだけを生存判定に使っていた。`/FO CSV /NH` のPID列を構造的に解析し、PID一致を確認できない出力・異常終了・不正出力は安全にfalseとした。ProcessHandleを使えるJava 9+経路は従来どおり優先し、Java 8ではこのOS fallbackのみを使用する。
- 1.19 / 1.19.1: 親プロセス終了検知とreplacement起動の間に、canonicalと同じ2,000msのlauncher settle待機を追加した。
- 26.1.x / 26.2: Fabric adapterがProcessHandleの引数だけを要求していたため、JVM引数境界・classpath・Fabric loaderの解決済みアプリケーション引数から再起動ベクトルを構成して共通サービスへ渡す形に同期した。共通サービスのloader引数優先、process引数fallback、専用launcher検出、未対応launcherの安全なJava判定は維持した。開発環境を理由に安全ガードを無効化する変更は行っていない。
- 26.1.x / 26.2: BackupListScreenで復元結果を設定した後に `setScreen(this)` が `init` と一覧reloadを実行し、statusが空へ戻る順序バグを修正した。復元結果を再初期化をまたいで一度だけ保持し、通常のrefresh・cancel・次回initへ古いstatusを持ち越さない。
- 全Fabric 28版とmainのFabric 1.20.1: `screen.universal_config.reorder_failed` を5言語へ追加した。

## 対象版

Fabricの28対象ディレクトリは次のとおりである。`1.16.5, 1.18.1, 1.18.2, 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6, 1.21, 1.21.1, 1.21.10, 1.21.11, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 26.1.x, 26.2`。

各版の `en_us`, `ja_jp`, `ko_kr`, `zh_cn`, `zh_tw` はJSONとして読み込み可能で、各版5言語のキー集合は一致し、`reorder_failed` を含む148キーとなった。

## Canonical判定

再起動settle、Java引数の境界保持、loader引数優先、復元確認後の一覧復帰と結果保持、cancel時の状態破棄、5言語キー集合を今回の基準へ同期した。`1.16.5` のPID判定はJava 8でリンク可能な反射・OS分岐を維持し、Java 9+のProcessHandle引数取得も公開インターフェース経由である。

## Issue #55

今回の対象は上記の既存canonical差分に限定した。Prism/MultiMC/ATLauncher等の追加統合や、Issue #55で別途扱うlauncher固有機能は実装していない。
