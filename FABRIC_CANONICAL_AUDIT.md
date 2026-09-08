# Fabric Canonical仕様: 監査記録（変更前差分・最終同期結果）

Core基準: origin/main/common。各行のSHAのactual codeを参照。過去Issueの判定は使用していない。

## Git戦略

A: origin/fabricの8版はfix/fabric-canonical-spec-sync。B: 未統合19版はそれぞれ元feature branchのSHAを親にfix/fabric-canonical-spec-sync-<version>を作る。既存worktreeと元feature branchは変更しない。origin/fabricへmergeしない。

## Canonical（変更前差分）

Apply系の表示。復元は確認必須、Noは復元せず一覧へ戻る。Yesは成功・失敗とも一覧を再読込して結果を保持し、確認コールバックの再実行を防ぐ。プロフィール0件でもBackupsへ到達する。Escは親画面へ戻る。日時不明は既存screen.universal_config.date_unknownを再利用する。再起動は有効なloader引数を優先し、無効なら境界を保持したprocess引数へfallbackする。終了後2秒のlauncher settle待機を維持する。

|Version|Java args fallback|launcher wait|settle wait|Apply wording|Restore confirm|Yes後return|zero-profile Backups|unknown i18n|Esc/onClose|manifest null guard|5 lang|
|---|---|---|---|---|---|---|---|---|---|---|---|
|1.16.5|要修正|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.18.2|要修正|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.19.2|要修正|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.20.1|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|142 keys / 集合一致|
|1.20.4|要修正|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.21.1|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|142 keys / 集合一致|
|26.1.x|OK|OK|OK|OK|OK|要修正|要修正|要修正|OK|OK|147 keys / 集合一致|
|26.2|OK|OK|OK|OK|OK|要修正|要修正|要修正|OK|OK|147 keys / 集合一致|
|1.19|要修正|要修正|要修正|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.19.1|要修正|要修正|要修正|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.19.3|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.19.4|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.20.2|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.20.3|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.20.5|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.20.6|OK|OK|OK|OK|OK|要修正|要修正|要修正|要修正|OK|147 keys / 集合一致|
|1.21|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.10|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.11|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|141 keys / 集合一致|
|1.21.2|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.3|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.4|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.5|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.6|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.7|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.8|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|
|1.21.9|OK|OK|OK|要修正|OK|要修正|要修正|要修正|要修正|OK|143 keys / 集合一致|

## 参照コミット

- 1.16.5: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.18.2: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.19.2: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.20.1: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.20.4: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.21.1: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 26.1.x: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 26.2: `origin/fabric` / `cc2cef3e0b86b80afef3d3dbe8cb18b8bf5c9e01`
- 1.19: `origin/feature/fabric-1-19` / `5b5346a3d175651eef710b7c1ce192240f2a8289`
- 1.19.1: `origin/feature/fabric-1-19-1` / `b4591317191904ac12f0b3cbba8ad87adb272c0b`
- 1.19.3: `origin/feature/fabric-1-19-3` / `a716964716e0fb303ada0e5611af542c46de6c96`
- 1.19.4: `origin/feature/fabric-1-19-4` / `36873429888289243b363a93030798a4b079c482`
- 1.20.2: `origin/feature/fabric-1-20-2` / `cfce16fcfc9c8c0c12505ed3d5394f18db5fb9d2`
- 1.20.3: `origin/feature/fabric-1-20-3` / `e2a1539d4fad85854f82fdd55632601cd160cdee`
- 1.20.5: `origin/feature/fabric-1-20-5` / `2d2b4fb83d27cd7c1d6a8bb09d5494e90da72ac9`
- 1.20.6: `origin/feature/fabric-1-20-6` / `9c70d5c6d003f83847b01884c055091a9db7baa8`
- 1.21: `origin/feature/fabric-1-21` / `b5c63cd4b33b5f59a1203915360bc95a0b13f6fa`
- 1.21.10: `origin/feature/fabric-1-21-10` / `90f13e9487b7234743b163b6e2552744d48426d1`
- 1.21.11: `origin/feature/fabric-1-21-11` / `425bcb6ad19f0fab22f66bb9287e5c1da6bcc143`
- 1.21.2: `origin/feature/fabric-1-21-2` / `929afcb5d55ae7b8be2d7a4d3ca5f70700f7907c`
- 1.21.3: `origin/feature/fabric-1-21-3` / `987ebc0b34192e5fe1e39f26056efa55eea419e1`
- 1.21.4: `origin/feature/fabric-1-21-4` / `ff6ad03ac33ddc97de445040bbe0f483f53f389c`
- 1.21.5: `origin/feature/fabric-1-21-5` / `3657d4cc6ca9fc2a73acbee555b4e4fe260086a4`
- 1.21.6: `origin/feature/fabric-1-21-6` / `b40ef3573c3a2356547ca9faf99f0cd252ed7994`
- 1.21.7: `origin/feature/fabric-1-21-7` / `3ac714df9e1f8fdd03f1f461c9f405184a6c32c5`
- 1.21.8: `origin/feature/fabric-1-21-8` / `d8b17c6515a35a3fec4df88dee16390c7685c0dd`
- 1.21.9: `origin/feature/fabric-1-21-9` / `494f5c1a177b2ab8d08790a703f599f39b2a7a00`

## 最終確認

1.18.1というローカルbranch名はあるが、現在のorigin/fabricおよびfeature対象の版別ディレクトリには存在しないため、この27ディレクトリ監査の対象外。ルートsrcはversion directoryではない旧テンプレートで今回の版別修正対象外。1.16.5のみJava8CompatによりProcessHandleのprocess-arguments fallbackを導入せず、loader-resolved arguments経路を維持した。

### 同期後の判定

上表の変更前「要修正」は、今回の版別修正後に全て解消した。27対象ディレクトリで、Apply表現、Restore確認後の一覧復帰とstatus保持、空ProfileからのBackups到達、unknown日時の翻訳、Esc戻り、manifest null guard、5言語147キー集合一致を再確認した。ProcessHandle対応版は有効なloader引数を優先し、無効時はprocess引数へfallbackする実装に統一した。

Java 21必須版は環境のJDK25と利用可能なGradle 9.5.1で検証した。1.20.5/1.20.6は固定wrapperのGradle 8.13がJDK25の実行に対応しないため、wrapperを変更せずGradle 9.5.1を明示して検証した。

表のmanifest列はBackupListのnull guardを表す。アーカイブの検証は別途core比較とテストで評価する。launcher waitはCanonicalと同じparent exit後のsettle待機を表す。
